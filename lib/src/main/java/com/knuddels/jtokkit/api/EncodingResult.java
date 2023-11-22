package com.knuddels.jtokkit.api;


import it.unimi.dsi.fastutil.ints.IntList;

/**
 * The result of encoding operation.
 */
public final class EncodingResult {
    private final IntList tokens;
    private final boolean truncated;
    private int tokenCount;

    public EncodingResult(IntList tokens, int tokenCount, boolean truncated) {
        this.tokens = tokens;
        this.tokenCount = tokenCount;
        this.truncated = truncated;
    }

    /**
     * @return the list of token ids
     */
    public IntList getTokens() {
        if (tokens.size() != getTokenCount()) {
            throw new IllegalStateException("Token count does not match token list size");
        }
        return tokens;
    }

    public int getTokenCount() {
        if (tokenCount < 0) {
            tokenCount = tokens.size();
        }
        return tokenCount;
    }

    /**
     * @return true if the token list was truncated because the maximum token length was exceeded
     */
    public boolean isTruncated() {
        return truncated;
    }

    @Override
    public String toString() {
        return "EncodingResult{" +
                "tokens=" + tokens +
                ", tokenCount=" + tokenCount +
                ", truncated=" + truncated +
                '}';
    }
}
