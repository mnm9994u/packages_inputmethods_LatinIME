/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.regex.Pattern;

/**
 * Utility methods to deal with editing text through an InputConnection.
 */
public class EditingUtils {
    /**
     * Number of characters we want to look back in order to identify the previous word
     */
    // Provision for a long word pair and a separator
    private static final int LOOKBACK_CHARACTER_NUM = BinaryDictionary.MAX_WORD_LENGTH * 2 + 1;
    private static final int INVALID_CURSOR_POSITION = -1;

    private EditingUtils() {
        // Unintentional empty constructor for singleton.
    }

    private static int getCursorPosition(InputConnection connection) {
        if (null == connection) return INVALID_CURSOR_POSITION;
        final ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted == null) {
            return INVALID_CURSOR_POSITION;
        }
        return extracted.startOffset + extracted.selectionStart;
    }

    /**
     * @param connection connection to the current text field.
     * @param separators characters which may separate words
     * @return the word that surrounds the cursor, including up to one trailing
     *   separator. For example, if the field contains "he|llo world", where |
     *   represents the cursor, then "hello " will be returned.
     */
    public static String getWordAtCursor(InputConnection connection, String separators) {
        // getWordRangeAtCursor returns null if the connection is null
        Range r = getWordRangeAtCursor(connection, separators, 0);
        return (r == null) ? null : r.mWord;
    }

    /**
     * Represents a range of text, relative to the current cursor position.
     */
    public static class Range {
        /** Characters before selection start */
        public final int mCharsBefore;

        /**
         * Characters after selection start, including one trailing word
         * separator.
         */
        public final int mCharsAfter;

        /** The actual characters that make up a word */
        public final String mWord;

        public Range(int charsBefore, int charsAfter, String word) {
            if (charsBefore < 0 || charsAfter < 0) {
                throw new IndexOutOfBoundsException();
            }
            this.mCharsBefore = charsBefore;
            this.mCharsAfter = charsAfter;
            this.mWord = word;
        }
    }

    /**
     * Returns the text surrounding the cursor.
     *
     * @param connection the InputConnection to the TextView
     * @param sep a string of characters that split words.
     * @param additionalPrecedingWordsCount the number of words before the current word that should
     *   be included in the returned range
     * @return a range containing the text surrounding the cursor
     */
    public static Range getWordRangeAtCursor(InputConnection connection, String sep,
            int additionalPrecedingWordsCount) {
        if (connection == null || sep == null) {
            return null;
        }
        CharSequence before = connection.getTextBeforeCursor(1000, 0);
        CharSequence after = connection.getTextAfterCursor(1000, 0);
        if (before == null || after == null) {
            return null;
        }

        // Going backward, alternate skipping non-separators and separators until enough words
        // have been read.
        int start = before.length();
        boolean isStoppingAtWhitespace = true;  // toggles to indicate what to stop at
        while (true) { // see comments below for why this is guaranteed to halt
            while (start > 0) {
                final int codePoint = Character.codePointBefore(before, start);
                if (isStoppingAtWhitespace == isSeparator(codePoint, sep)) {
                    break;  // inner loop
                }
                --start;
                if (Character.isSupplementaryCodePoint(codePoint)) {
                    --start;
                }
            }
            // isStoppingAtWhitespace is true every other time through the loop,
            // so additionalPrecedingWordsCount is guaranteed to become < 0, which
            // guarantees outer loop termination
            if (isStoppingAtWhitespace && (--additionalPrecedingWordsCount < 0)) {
                break;  // outer loop
            }
            isStoppingAtWhitespace = !isStoppingAtWhitespace;
        }

        // Find last word separator after the cursor
        int end = -1;
        while (++end < after.length()) {
            final int codePoint = Character.codePointAt(after, end);
            if (isSeparator(codePoint, sep)) {
                break;
            }
            if (Character.isSupplementaryCodePoint(codePoint)) {
                ++end;
            }
        }

        int cursor = getCursorPosition(connection);
        if (start >= 0 && cursor + end <= after.length() + before.length()) {
            String word = before.toString().substring(start, before.length())
                    + after.toString().substring(0, end);
            return new Range(before.length() - start, end, word);
        }

        return null;
    }

    private static boolean isSeparator(int code, String sep) {
        return sep.indexOf(code) != -1;
    }

    private static final Pattern spaceRegex = Pattern.compile("\\s+");

    public static CharSequence getPreviousWord(InputConnection connection,
            String sentenceSeperators) {
        //TODO: Should fix this. This could be slow!
        if (null == connection) return null;
        CharSequence prev = connection.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        return getPreviousWord(prev, sentenceSeperators);
    }

    // Get the word before the whitespace preceding the non-whitespace preceding the cursor.
    // Also, it won't return words that end in a separator.
    // Example :
    // "abc def|" -> abc
    // "abc def |" -> abc
    // "abc def. |" -> abc
    // "abc def . |" -> def
    // "abc|" -> null
    // "abc |" -> null
    // "abc. def|" -> null
    public static CharSequence getPreviousWord(CharSequence prev, String sentenceSeperators) {
        if (prev == null) return null;
        String[] w = spaceRegex.split(prev);

        // If we can't find two words, or we found an empty word, return null.
        if (w.length < 2 || w[w.length - 2].length() <= 0) return null;

        // If ends in a separator, return null
        char lastChar = w[w.length - 2].charAt(w[w.length - 2].length() - 1);
        if (sentenceSeperators.contains(String.valueOf(lastChar))) return null;

        return w[w.length - 2];
    }

    public static CharSequence getThisWord(InputConnection connection, String sentenceSeperators) {
        if (null == connection) return null;
        final CharSequence prev = connection.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        return getThisWord(prev, sentenceSeperators);
    }

    // Get the word immediately before the cursor, even if there is whitespace between it and
    // the cursor - but not if there is punctuation.
    // Example :
    // "abc def|" -> def
    // "abc def |" -> def
    // "abc def. |" -> null
    // "abc def . |" -> null
    public static CharSequence getThisWord(CharSequence prev, String sentenceSeperators) {
        if (prev == null) return null;
        String[] w = spaceRegex.split(prev);

        // No word : return null
        if (w.length < 1 || w[w.length - 1].length() <= 0) return null;

        // If ends in a separator, return null
        char lastChar = w[w.length - 1].charAt(w[w.length - 1].length() - 1);
        if (sentenceSeperators.contains(String.valueOf(lastChar))) return null;

        return w[w.length - 1];
    }
}
