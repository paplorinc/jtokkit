package com.knuddels.jtokkit;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GptBytePairEncodingOriginal {
    public final Pattern pattern;
    private final String name;
    private final TokenEncoderOriginal<ImmutableByteArray, Integer> encoder;
    private final TokenEncoderOriginal<String, Integer> specialTokensEncoder;

    public GptBytePairEncodingOriginal(String name, Pattern pattern, Map<byte[], Integer> encoder, Map<String, Integer> specialTokensEncoder) {
        this.name = name;
        this.pattern = pattern;
        this.encoder = new TokenEncoderOriginal<>(encoder, ImmutableByteArray::from);
        this.specialTokensEncoder = new TokenEncoderOriginal<>(specialTokensEncoder);
    }

    public static GptBytePairEncodingOriginal getEncoder() {
        var originalRegex = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";
        var regex = Pattern.compile(originalRegex, Pattern.UNICODE_CHARACTER_CLASS);

        var encoder = EncodingFactory.loadMergeableRanks("/com/knuddels/jtokkit/cl100k_base.tiktoken");
        return new GptBytePairEncodingOriginal("cl100k_base", regex, encoder, EncodingFactory.SPECIAL_TOKENS_CL100K_BASE);
    }


    public List<Integer> encode(final String text) {
        return encodeInternal(text, null).getTokens();
    }

    public EncodingResult encode(final String text, final int maxTokens) {
        return encodeInternal(text, maxTokens);
    }

    private EncodingResult encodeInternal(final String text, final Integer maxTokens) {
        if (text == null) {
            return new EncodingResult(Collections.emptyList(), false);
        }

        for (final String specialToken : specialTokensEncoder.getDecodedTokens()) {
            if (text.contains(specialToken)) {
                throw new UnsupportedOperationException("Encoding special tokens is not supported yet.");
            }
        }

        return encodeOrdinaryInternal(text, maxTokens);
    }

    public List<Integer> encodeOrdinary(final String text) {
        return encodeOrdinaryInternal(text, null).getTokens();
    }

    public EncodingResult encodeOrdinary(final String text, final int maxTokens) {
        return encodeOrdinaryInternal(text, maxTokens);
    }

    private EncodingResult encodeOrdinaryInternal(final String text, final Integer maxTokens) {
        if (text == null) {
            return new EncodingResult(Collections.emptyList(), false);
        }

        final List<Integer> out = new ArrayList<>();
        final Matcher matcher = pattern.matcher(text);
        int tokenCount = 0;
        while (matcher.find() && maxTokenCountNotReached(maxTokens, tokenCount)) {
            String group = matcher.group();
            ImmutableByteArray match = ImmutableByteArray.from(group);
            if (encoder.containsDecodedToken(match)) {
                out.add(encoder.encode(match));
                tokenCount++;
            } else {
                final List<Integer> tokensToAdd = bytePairMerge(match);
                tokenCount += addTokens(out, tokensToAdd, maxTokens);
            }
        }

        if (maxTokens != null) {
            for (int tokensToRemove = 0; tokensToRemove <= out.size(); tokensToRemove++) {
                final List<Integer> tokens = out.subList(0, out.size() - tokensToRemove);
                final String decoded = decode(tokens);
                if (text.startsWith(decoded)) {
                    return new EncodingResult(tokens, text.length() > decoded.length());
                }
            }
        }

        return new EncodingResult(out, false);
    }

    private int addTokens(final List<Integer> out, final List<Integer> tokensToAdd, final Integer maxTokens) {
        if (maxTokens != null) {
            final List<Integer> sublist = tokensToAdd.subList(0, Math.min(maxTokens - out.size(), tokensToAdd.size()));
            out.addAll(sublist);
            return sublist.size();
        }

        out.addAll(tokensToAdd);
        return tokensToAdd.size();
    }


    public int countTokens(final String text) {
        return encode(text).size();
    }


    public long countSplitChars(String text) {
        return 0;
    }


    public int countTokensOrdinary(final String text) {
        return encodeOrdinary(text).size();
    }


    public String decode(final List<Integer> tokens) {
        return new String(decodeBytes(tokens), StandardCharsets.UTF_8);
    }


    public byte[] decodeBytes(final List<Integer> tokens) {
        final List<Byte> out = new ArrayList<>();
        for (final int token : tokens) {
            final byte[] decodedToken = decodeToken(token);
            for (final byte b : decodedToken) {
                out.add(b);
            }
        }

        final byte[] outArray = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            outArray[i] = out.get(i);
        }
        return outArray;
    }


    public String getName() {
        return name;
    }

    private List<Integer> bytePairMerge(final ImmutableByteArray piece) {
        final List<PieceIndexToRank> parts = new ArrayList<>();
        for (int i = 0; i < piece.length() + 1; i++) {
            parts.add(new PieceIndexToRank(i, Integer.MAX_VALUE));
        }

        for (int i = 0; i < parts.size() - 2; i++) {
            final Optional<Integer> rank = getRank(piece, parts, i, 0);
            if (rank.isPresent()) {
                parts.get(i).rank = rank.get();
            }
        }

        while (parts.size() > 1) {
            int minRankIndex = 0;
            int minRank = Integer.MAX_VALUE;
            for (int i = 0; i < parts.size() - 1; i++) {
                final int rank = parts.get(i).rank;
                if (rank < minRank) {
                    minRank = rank;
                    minRankIndex = i;
                }
            }

            if (minRank != Integer.MAX_VALUE) {
                parts.get(minRankIndex).rank = getRank(piece, parts, minRankIndex, 1).orElse(Integer.MAX_VALUE);
                if (minRankIndex > 0) {
                    parts.get(minRankIndex - 1).rank = getRank(piece, parts, minRankIndex - 1, 1).orElse(Integer.MAX_VALUE);
                }

                parts.remove(minRankIndex + 1);
            } else {
                break;
            }
        }

        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < parts.size() - 1; i++) {
            out.add(encoder.encode(piece.getBytesBetween(parts.get(i).index, parts.get(i + 1).index)));
        }
        return out;
    }

    private boolean maxTokenCountReached(final Integer maxTokenCount, final int tokenCount) {
        return maxTokenCount != null && maxTokenCount.compareTo(tokenCount) <= 0;
    }

    private boolean maxTokenCountNotReached(final Integer maxTokenCount, final int tokenCount) {
        return !maxTokenCountReached(maxTokenCount, tokenCount);
    }

    private Optional<Integer> getRank(
            final ImmutableByteArray piece,
            final List<PieceIndexToRank> parts,
            final int startIndex,
            final int skip
    ) {
        if (startIndex + skip + 2 >= parts.size()) {
            return Optional.empty();
        }

        final int pieceStartIndex = parts.get(startIndex).index;
        final int pieceEndIndex = parts.get(startIndex + skip + 2).index;
        final ImmutableByteArray encoderIndex = piece.getBytesBetween(pieceStartIndex, pieceEndIndex);

        return encoder.encodeIfPresent(encoderIndex);
    }

    private byte[] decodeToken(final int token) {
        final Optional<ImmutableByteArray> decodedToken = encoder.decodeIfPresent(token);
        if (decodedToken.isPresent()) {
            return decodedToken.get().getRawArray();
        }

        final Optional<String> decodedSpecialToken = specialTokensEncoder.decodeIfPresent(token);
        if (decodedSpecialToken.isPresent()) {
            return decodedSpecialToken.get().getBytes(StandardCharsets.UTF_8);
        }

        throw new IllegalArgumentException("Unknown token for decoding: " + token);
    }

    private static class PieceIndexToRank {
        private final int index;
        private int rank;

        public PieceIndexToRank(final int index, final int rank) {
            this.index = index;
            this.rank = rank;
        }
    }

    private static final class ImmutableByteArray {
        private final byte[] array;

        /*
         * Creates a new instance of ImmutableByteArray from the given array.
         * The given array is not copied, so every calling method in this class must make sure
         * to never pass an array which reference leaked to the outside. Since some of our
         * construction methods already create new arrays, we do not want to copy here in this
         * constructor again.
         */
        private ImmutableByteArray(final byte[] array) {
            this.array = array;
        }

        public static ImmutableByteArray from(final String string) {
            Objects.requireNonNull(string, "String must not be null");
            return new ImmutableByteArray(string.getBytes(StandardCharsets.UTF_8));
        }

        public static ImmutableByteArray from(final byte[] array) {
            Objects.requireNonNull(array, "Byte array must not be null");
            return new ImmutableByteArray(array.clone());
        }

        public int length() {
            return array.length;
        }

        public ImmutableByteArray getBytesBetween(final int startIndex, final int endIndex) {
            if (startIndex < 0 || startIndex >= array.length) {
                throw new IndexOutOfBoundsException("startIndex out of bounds: " + startIndex + " (" + this + ")");
            }

            if (endIndex < 0 || endIndex > array.length) {
                throw new IndexOutOfBoundsException("endIndex out of bounds: " + endIndex + " (" + this + ")");
            }

            if (startIndex >= endIndex) {
                throw new IllegalArgumentException("startIndex must be less than endIndex: " + startIndex + " >= " + endIndex);
            }

            final int length = endIndex - startIndex;
            final byte[] result = new byte[length];
            System.arraycopy(array, startIndex, result, 0, length);
            return new ImmutableByteArray(result);
        }

        public byte[] getRawArray() {
            return array.clone();
        }


        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }

            if (other == null || getClass() != other.getClass()) {
                return false;
            }

            final ImmutableByteArray that = (ImmutableByteArray) other;
            return Arrays.equals(array, that.array);
        }


        public int hashCode() {
            return Arrays.hashCode(array);
        }


        public String toString() {
            return Arrays.toString(array);
        }
    }

    private final static class TokenEncoderOriginal<K, V> {

        private final Map<K, V> decodedToEncoded = new HashMap<>();
        private final Map<V, K> encodedToDecoded = new HashMap<>();

        public TokenEncoderOriginal(final Map<K, V> input) {
            this(input, Function.identity());
        }

        public <T> TokenEncoderOriginal(final Map<T, V> input, final Function<T, K> keyMapper) {
            for (final Map.Entry<T, V> entry : input.entrySet()) {
                final K key = keyMapper.apply(entry.getKey());
                final V value = entry.getValue();
                decodedToEncoded.put(key, value);
                encodedToDecoded.put(value, key);
            }
        }

        public boolean containsDecodedToken(final K decodedToken) {
            return decodedToEncoded.containsKey(decodedToken);
        }

        public V encode(final K decodedToken) {
            final V encoded = decodedToEncoded.get(decodedToken);
            if (encoded == null) {
                throw new IllegalArgumentException("Unknown token for encoding: " + decodedToken);
            }

            return encoded;
        }

        public Optional<V> encodeIfPresent(final K decodedToken) {
            return Optional.ofNullable(decodedToEncoded.get(decodedToken));
        }

        public Optional<K> decodeIfPresent(final V encodedToken) {
            return Optional.ofNullable(encodedToDecoded.get(encodedToken));
        }

        public Set<K> getDecodedTokens() {
            return Collections.unmodifiableSet(decodedToEncoded.keySet());
        }
    }

    public final class EncodingResult {
        private final List<Integer> tokens;
        private final boolean truncated;

        public EncodingResult(final List<Integer> tokens, final boolean truncated) {
            this.tokens = tokens;
            this.truncated = truncated;
        }

        public List<Integer> getTokens() {
            return tokens;
        }

        public boolean isTruncated() {
            return truncated;
        }


        public String toString() {
            return "EncodingResult{"
                    + "tokens=" + tokens
                    + ", truncated=" + truncated
                    + '}';
        }
    }
}
