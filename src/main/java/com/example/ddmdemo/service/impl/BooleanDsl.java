package com.example.ddmdemo.service.impl;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.elasticsearch.common.unit.Fuzziness;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Boolean DSL -> ES Query (shunting-yard)
 * Podrška: AND/OR/NOT, zagrade, "fraze", field:term i field:"fraza".
 */
public class BooleanDsl {

    private static final Set<String> OPS = Set.of("AND", "OR", "NOT");

    private static final Map<String, Integer> PREC = Map.of(
            "NOT", 3, "AND", 2, "OR", 1
    );

    private static final Pattern TOKENIZER = Pattern.compile(
            "\\(|\\)|(?i:AND|OR|NOT)|\\w+:\"[^\"]+\"|\\w+:[^\\s()]+|\"[^\"]+\"|[^\\s()]+"
    );

    private final Set<String> keywordFields;

    private final List<String> textFields;

    private final List<String> allSearchFields;

    public BooleanDsl(Set<String> keywordFields, List<String> textFields, List<String> allSearchFields) {
        this.keywordFields = keywordFields;
        this.textFields = textFields;
        this.allSearchFields = allSearchFields;
    }

    public Query parseToQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return QueryBuilders.matchAll(m -> m);
        }
        List<Token> tokens = tokenize(raw);
        List<Token> rpn = toRpn(tokens);
        return buildFromRpn(rpn);
    }

    private enum T { TERM, PHRASE, FIELD_TERM, FIELD_PHRASE, OP, LPAREN, RPAREN }
    private record Token(T t, String v) {}

    private List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        Matcher m = TOKENIZER.matcher(s);
        while (m.find()) {
            String tok = m.group();
            if ("(".equals(tok)) out.add(new Token(T.LPAREN, tok));
            else if (")".equals(tok)) out.add(new Token(T.RPAREN, tok));
            else if (isOp(tok)) out.add(new Token(T.OP, tok.toUpperCase()));
            else if (tok.startsWith("\"") && tok.endsWith("\"")) {
                out.add(new Token(T.PHRASE, stripQuotes(tok)));
            } else if (tok.contains(":")) {
                String[] parts = tok.split(":", 2);
                String field = parts[0];
                String val = parts[1];
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    out.add(new Token(T.FIELD_PHRASE, field + ":" + stripQuotes(val)));
                } else {
                    out.add(new Token(T.FIELD_TERM, field + ":" + val));
                }
            } else {
                out.add(new Token(T.TERM, tok));
            }
        }
        return out;
    }

    private boolean isOp(String s) {
        return OPS.contains(s.toUpperCase());
    }

    private String stripQuotes(String q) {
        return q.substring(1, q.length() - 1);
    }

    private List<Token> toRpn(List<Token> in) {
        List<Token> out = new ArrayList<>();
        Deque<Token> stack = new ArrayDeque<>();

        for (int i = 0; i < in.size(); i++) {
            Token tk = in.get(i);
            switch (tk.t) {
                case TERM, PHRASE, FIELD_TERM, FIELD_PHRASE -> out.add(tk);
                case OP -> {
                    String op = tk.v;
                    while (!stack.isEmpty() && stack.peek().t == T.OP) {
                        String top = stack.peek().v;
                        if (PREC.get(top) >= PREC.get(op)) out.add(stack.pop());
                        else break;
                    }
                    stack.push(tk);
                }
                case LPAREN -> stack.push(tk);
                case RPAREN -> {
                    while (!stack.isEmpty() && stack.peek().t != T.LPAREN) {
                        out.add(stack.pop());
                    }
                    if (stack.isEmpty() || stack.peek().t != T.LPAREN) {
                        throw new IllegalArgumentException("Neusaglašene zagrade");
                    }
                    stack.pop();
                }
            }
        }
        while (!stack.isEmpty()) {
            Token t = stack.pop();
            if (t.t == T.LPAREN || t.t == T.RPAREN) {
                throw new IllegalArgumentException("Neusaglašene zagrade");
            }
            out.add(t);
        }
        return out;
    }

    private Query buildFromRpn(List<Token> rpn) {
        Deque<Query> st = new ArrayDeque<>();

        for (Token tk : rpn) {
            switch (tk.t) {
                case TERM -> st.push(buildTermQuery(null, tk.v));
                case PHRASE -> st.push(buildPhraseQuery(null, tk.v));
                case FIELD_TERM -> {
                    String[] fv = tk.v.split(":", 2);
                    st.push(buildTermQuery(fv[0], fv[1]));
                }
                case FIELD_PHRASE -> {
                    String[] fv = tk.v.split(":", 2);
                    st.push(buildPhraseQuery(fv[0], fv[1]));
                }
                case OP -> {
                    String op = tk.v;
                    if ("NOT".equals(op)) {
                        Query a = st.pop(); // unarni
                        st.push(boolNot(a));
                    } else if ("AND".equals(op)) {
                        Query b = st.pop(), a = st.pop();
                        st.push(boolAnd(a, b));
                    } else if ("OR".equals(op)) {
                        Query b = st.pop(), a = st.pop();
                        st.push(boolOr(a, b));
                    }
                }
                default -> throw new IllegalStateException("Nepodržan token: " + tk);
            }
        }
        if (st.isEmpty()) return QueryBuilders.matchAll(m -> m);
        return st.pop();
    }

    private Query buildTermQuery(String field, String value) {
        if (field == null) {
            // multi-field: text polja -> match, keyword polja -> term
            BoolQuery.Builder any = new BoolQuery.Builder();
            for (String f : textFields) {
                any.should(s -> s.match(m -> m.field(f).query(value).fuzziness(Fuzziness.AUTO.asString())));
            }
            for (String kf : keywordFields) {
                any.should(s -> s.term(t -> t.field(kf).value(value)));
            }
            any.minimumShouldMatch("1");
            return any.build()._toQuery();
        } else {
            if (keywordFields.contains(field)) {
                return TermQuery.of(t -> t.field(field).value(value))._toQuery();
            } else {
                return MatchQuery.of(m -> m.field(field).query(value).fuzziness(Fuzziness.AUTO.asString()))._toQuery();
            }
        }
    }

    private Query buildPhraseQuery(String field, String phrase) {
        if (field == null) {
            BoolQuery.Builder any = new BoolQuery.Builder();
            for (String f : textFields) {
                any.should(s -> s.matchPhrase(mp -> mp.field(f).query(phrase)));
            }
            any.minimumShouldMatch("1");
            return any.build()._toQuery();
        } else {
            if (keywordFields.contains(field)) {
                return TermQuery.of(t -> t.field(field).value(phrase))._toQuery();
            } else {
                return MatchPhraseQuery.of(mp -> mp.field(field).query(phrase))._toQuery();
            }
        }
    }

    private Query boolAnd(Query a, Query b) {
        return BoolQuery.of(bq -> bq.must(a).must(b))._toQuery();
    }
    private Query boolOr(Query a, Query b) {
        return BoolQuery.of(bq -> bq.should(a).should(b).minimumShouldMatch("1"))._toQuery();
    }
    private Query boolNot(Query a) {
        return BoolQuery.of(bq -> bq.mustNot(a))._toQuery();
    }

    public Query simpleQueryString(String raw) {
        return QueryBuilders.simpleQueryString(s -> s
                .query(raw)
                .fields(allSearchFields)
                .defaultOperator(Operator.And)
                .lenient(true)
                .analyzeWildcard(true)
                .minimumShouldMatch("1")
        );
    }
}