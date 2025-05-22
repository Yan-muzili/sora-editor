package com.yan.ylua;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
public class MyPrefixChecker implements CompletionHelper.PrefixChecker {
    @Override
    public boolean check(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '.';
    }
}
