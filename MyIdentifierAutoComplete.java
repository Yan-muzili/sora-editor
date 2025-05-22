package com.yan.ylua;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.completion.Comparators;
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem;
import io.github.rosemoe.sora.lang.completion.Filters;
import io.github.rosemoe.sora.lang.completion.FuzzyScoreOptions;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import java.lang.String;
import java.lang.Object;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.util.MutableInt;

/**
 * Identifier auto-completion.
 *
 * <p>You can use it to provide identifiers, but you can't update the given {@link
 * CompletionPublisher} if it is used. If you have to mix the result, then you should call {@link
 * CompletionPublisher#setComparator(Comparator)} with null first. Otherwise, your completion list
 * may be corrupted. And in that case, you must do the sorting work by yourself and then add your
 * items.
 *
 * @author Rosemoe
 */
public class MyIdentifierAutoComplete {

  /**
   * @deprecated Use {@link Comparators}
   */
  @Deprecated
  private static final Comparator<CompletionItem> COMPARATOR =
      (p1, p2) -> {
        var cmp1 = asString(p1.desc).compareTo(asString(p2.desc));
        if (cmp1 < 0) {
          return 1;
        } else if (cmp1 > 0) {
          return -1;
        }
        return asString(p1.label).compareTo(asString(p2.label));
      };

  private String[] keywords;
  private boolean keywordsAreLowCase;
  private Map<String, Object> keywordMap;

  public MyIdentifierAutoComplete() {}

  public MyIdentifierAutoComplete(String[] keywords) {
    this();
    setKeywords(keywords, true);
  }

  private static String asString(CharSequence str) {
    return (str instanceof String ? (String) str : str.toString());
  }

  public void setKeywords(String[] keywords, boolean lowCase) {
    this.keywords = keywords;
    keywordsAreLowCase = lowCase;
    var map = new HashMap<String, Object>();
    if (keywords != null) {
      for (var keyword : keywords) {
        map.put(keyword, true);
      }
    }
    keywordMap = map;
  }

  public String[] getKeywords() {
    return keywords;
  }

  public void write(String path, String cs) {
    try {
      FileOutputStream fos = new FileOutputStream(path, true);
      fos.write(cs.getBytes());
      fos.flush();
      fos.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Make completion items for the given arguments. Provide the required arguments passed by {@link
   * Language#requireAutoComplete(ContentReference, CharPosition, CompletionPublisher, Bundle)}
   *
   * @param prefix The prefix to make completions for.
   */
  public void requireAutoComplete(
      @NonNull ContentReference reference,
      @NonNull CharPosition position,
      @NonNull String prefix,
      @NonNull CompletionPublisher publisher,
      @Nullable Identifiers userIdentifiers) {
    var completionItemList = createCompletionItemList(prefix, userIdentifiers);

    var comparator =
        Comparators.getCompletionItemComparator(reference, position, completionItemList);

    publisher.addItems(completionItemList);

    publisher.setComparator(comparator);
  }

  private String extractLastIdentifier(String prefix) {
    int lastDotIndex = prefix.lastIndexOf('.');
    if (lastDotIndex != -1) {
      return prefix.substring(0, lastDotIndex);
    }
    return prefix;
  }

  public List<CompletionItem> createCompletionItemList(
      @NonNull String prefix, @Nullable Identifiers userIdentifiers) {
    int prefixLength = prefix.length();
    if (prefixLength == 0) {
      return Collections.emptyList();
    }
    var result = new ArrayList<CompletionItem>();
    final var keywordArray = keywords;
    final var lowCase = keywordsAreLowCase;
    final var keywordMap = this.keywordMap;
    var match = prefix.toLowerCase(Locale.ROOT);
    if (keywordArray != null) {
      if (lowCase) {
        for (var kw : keywordArray) {
          var fuzzyScore =
              Filters.fuzzyScoreGracefulAggressive(
                  prefix,
                  prefix.toLowerCase(Locale.ROOT),
                  0,
                  kw,
                  kw.toLowerCase(Locale.ROOT),
                  0,
                  FuzzyScoreOptions.getDefault());

          var score = fuzzyScore == null ? -100 : fuzzyScore.getScore();

          if (kw.startsWith(match) || score >= -20) {
            result.add(
                new SimpleCompletionItem(kw, "Keyword", prefixLength, kw)
                    .kind(CompletionItemKind.Keyword));
          }
        }
      } else {
        for (var kw : keywordArray) {
          var fuzzyScore =
              Filters.fuzzyScoreGracefulAggressive(
                  prefix,
                  prefix.toLowerCase(Locale.ROOT),
                  0,
                  kw,
                  kw.toLowerCase(Locale.ROOT),
                  0,
                  FuzzyScoreOptions.getDefault());

          var score = fuzzyScore == null ? -100 : fuzzyScore.getScore();

          if (kw.toLowerCase(Locale.ROOT).startsWith(match) || score >= -20) {
            result.add(
                new SimpleCompletionItem(kw, "Keyword", prefixLength, kw)
                    .kind(
                        (io.github.rosemoe.sora.lang.completion.CompletionItemKind)
                            CompletionItemKind.Keyword));
          }
        }
      }
    }
    if (userIdentifiers != null) {
      List<String> dest = new ArrayList<>();

      userIdentifiers.filterIdentifiers(prefix, dest);
      for (var word : dest) {
        if (keywordMap == null || !keywordMap.containsKey(word))
          result.add(
              new SimpleCompletionItem(word, "Identifier", prefixLength, word)
                  .kind(CompletionItemKind.Identifier));
      }
    }
    String[] luafunc = {
      "pcall",
      "load",
      "tostring",
      "tonumber",
      "error",
      "loadfile",
      "setmetatable",
      "pairs",
      "next",
      "assert",
      "rawlen",
      "ipairs",
      "xpcall",
      "rawequal",
      "getmetatable",
      "rawset",
      "type",
      "select",
      "dofile",
      "print"
    };
    for (String func : luafunc) {
      if (func.startsWith(prefix)) {
        result.add(
            new SimpleCompletionItem(func, "Lua function", prefixLength, func)
                .kind(CompletionItemKind.Function));
      }
    }
    String[] luaclass = {
      "io", "string", "os", "debug", "utf8", "table", "math", "bit32", "luajava", "gg"
    };
    for (String lclass : luaclass) {
      if (lclass.startsWith(prefix)) {
        result.add(
            new SimpleCompletionItem(lclass, "Lua class", prefixLength, lclass)
                .kind(CompletionItemKind.Class));
      }
    }
    // Check if the prefix is "io." and add luaio library functions
    String[] luaioFunctions = {
      "popen", "stdout", "lines", "write", "tmpfile", "open", "stderr", "close", "input", "read",
      "output", "flush", "type"
    };
    if (prefix.startsWith("io.")) {
      for (String func : luaioFunctions) {
        if (("io." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua Io Function", prefixLength, "io." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luastringFunctions = {
      "dump",
      "reverse",
      "char",
      "unpack",
      "match",
      "gsub",
      "find",
      "pack",
      "gmatch",
      "format",
      "packsize",
      "lower",
      "upper",
      "rep",
      "sub",
      "byte",
      "len"
    };
    if (prefix.startsWith("string.")) {

      for (String func : luastringFunctions) {
        if (("string." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua String Function", prefixLength, "string." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luaosFunctions = {
      "setlocale",
      "clock",
      "tmpname",
      "getenv",
      "execute",
      "difftime",
      "rename",
      "exit",
      "remove",
      "time",
      "date"
    };
    if (prefix.startsWith("os.")) {

      for (String func : luaosFunctions) {
        if (("os." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua Os Function", prefixLength, "os." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luadebugFunctions = {
      "getregistry",
      "getuservalue",
      "setuservalue",
      "getupvalue",
      "getinfo",
      "getlocal",
      "setlocal",
      "setupvalue",
      "traceback",
      "getmetatable",
      "setmetatable",
      "debug",
      "upvaluejoin",
      "sethook",
      "gethook",
      "upvalueid"
    };
    if (prefix.startsWith("debug.")) {

      for (String func : luadebugFunctions) {
        if (("debug." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua Debug Function", prefixLength, "debug." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luautf8Functions = {"codes", "offset", "char", "codepoint", "len", "charpattern"};
    if (prefix.startsWith("utf8.")) {

      for (String func : luautf8Functions) {
        if (("utf8." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua Utf8 Function", prefixLength, "utf8." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luatableFunctions = {"concat", "remove", "sort", "pack", "move", "insert", "unpack"};
    if (prefix.startsWith("table.")) {

      for (String func : luatableFunctions) {
        if (("table." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua Table Function", prefixLength, "table." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luamathFunctions = {
      "sqrt",
      "atan2",
      "ceil",
      "tanh",
      "rad",
      "pi",
      "abs",
      "sinh",
      "atan",
      "fmod",
      "mininteger",
      "random",
      "max",
      "randomseed",
      "modf",
      "deg",
      "exp",
      "ldexp",
      "cosh",
      "ult",
      "log",
      "tointeger",
      "frexp",
      "huge",
      "asin",
      "maxinteger",
      "tan",
      "floor",
      "pow",
      "acos",
      "cos",
      "type",
      "min",
      "sin"
    };
    if (prefix.startsWith("math.")) {

      for (String func : luamathFunctions) {
        if (("math." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua Math Function", prefixLength, "math." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luabit32Functions = {
      "rshift", "bnot", "lshift", "bxor", "btest", "extract", "lrotate", "rrotate", "band",
      "replace", "bor", "arshift"
    };
    if (prefix.startsWith("bit32.")) {

      for (String func : luabit32Functions) {
        if (("bit32." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua Bit32 Function", prefixLength, "bit32." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] lualuajavaFunctions = {
      "newInstance", "new", "bindClass", "astable", "createProxy", "loadLib"
    };
    if (prefix.startsWith("luajava.")) {

      for (String func : lualuajavaFunctions) {
        if (("luajava." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(
                      func, "Lua Luajava Function", prefixLength, "luajava." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    String[] luaggFunctions = {
      "getResultsCount",
      "SIGN_INEQUAL",
      "multiChoice",
      "setSpeed",
      "getSpeed",
      "REGION_C_HEAP",
      "bytes",
      "getFile",
      "require",
      "SIGN_LESS_OR_EQUAL",
      "TYPE_QWORD",
      "getValues",
      "setValues",
      "editAll",
      "SIGN_NOT_EQUAL",
      "getListItems",
      "loadResults",
      "TYPE_XOR",
      "getSelectedResults",
      "getTargetPackage",
      "internal3",
      "PROT_NONE",
      "startFuzzy",
      "processKill",
      "alert",
      "REGION_STACK",
      "CACHE_DIR",
      "setRanges",
      "getRanges",
      "REGION_CODE_SYS",
      "getLine",
      "unrandomizer",
      "getActiveTab",
      "ASM_THUMB",
      "skipRestoreState",
      "PROT_WRITE",
      "TYPE_BYTE",
      "REGION_C_DATA",
      "SIGN_GREATER_OR_EQUAL",
      "FILES_DIR",
      "FREEZE_IN_RANGE",
      "allocatePage",
      "getValuesRange",
      "PROT_READ",
      "REGION_OTHER",
      "REGION_VIDEO",
      "LOAD_VALUES",
      "internal1",
      "EXT_FILES_DIR",
      "REGION_BAD",
      "removeListItems",
      "getTargetInfo",
      "toast",
      "processResume",
      "makeRequest",
      "LOAD_VALUES_FREEZE",
      "getLocale",
      "getSelectedListItems",
      "timeJump",
      "processToggle",
      "disasm",
      "getResultCount",
      "FREEZE_MAY_DECREASE",
      "SIGN_FUZZY_EQUAL",
      "processPause",
      "FREEZE_MAY_INCREASE",
      "getRangesList",
      "isProcessPaused",
      "getResults",
      "TAB_SEARCH",
      "copyText",
      "REGION_CODE_APP",
      "POINTER_NO",
      "TAB_SAVED_LIST",
      "searchFuzzy",
      "BUILD",
      "internal2",
      "REGION_ASHMEM",
      "TYPE_AUTO",
      "REGION_ANONYMOUS",
      "clearResults",
      "SAVE_AS_TEXT",
      "removeResults",
      "refineAddress",
      "refineNumber",
      "SIGN_LARGER",
      "TYPE_FLOAT",
      "choice",
      "ASM_ARM",
      "SIGN_FUZZY_GREATER",
      "SIGN_EQUAL",
      "FREEZE_NORMAL",
      "LOAD_APPEND",
      "TYPE_WORD",
      "numberFromLocale",
      "POINTER_READ_ONLY",
      "POINTER_WRITABLE",
      "REGION_JAVA_HEAP",
      "getSelectedElements",
      "showUiButton",
      "isVisible",
      "gotoAddress",
      "SIGN_FUZZY_LESS",
      "getSelectedPackage",
      "TAB_MEMORY_EDITOR",
      "REGION_C_BSS",
      "saveList",
      "addListItems",
      "POINTER_EXECUTABLE",
      "prompt",
      "DUMP_SKIP_SYSTEM_LIBS",
      "setVisible",
      "REGION_PPSSPP",
      "REGION_JAVA",
      "searchNumber",
      "POINTER_EXECUTABLE_WRITABLE",
      "TYPE_DOUBLE",
      "PROT_EXEC",
      "saveVariable",
      "TAB_SETTINGS",
      "isPackageInstalled",
      "numberToLocale",
      "clearList",
      "copyMemory",
      "EXT_STORAGE",
      "SIGN_SMALLER",
      "REGION_C_ALLOC",
      "hideUiButton",
      "VERSION_INT",
      "ASM_ARM64",
      "sleep",
      "loadList",
      "SIGN_FUZZY_NOT_EQUAL",
      "TYPE_DWORD",
      "VERSION",
      "isClickedUiButton",
      "dumpMemory",
      "PACKAGE",
      "EXT_CACHE_DIR",
      "searchAddress"
    };
    if (prefix.startsWith("gg.")) {
      for (String func : luaggFunctions) {
        if (("gg." + func).startsWith(prefix)) {
          result.add(
              new SimpleCompletionItem(func, "Lua gg Function", prefixLength, "gg." + func)
                  .kind(CompletionItemKind.Function));
        }
      }
    }

    HashMap<String, Object> map = new HashMap<>();
    HashMap<String, Object> map2 = new HashMap<>();
    HashMap<String, Object> map3 = new HashMap<>();
    map3.put("xxx", "re");
    map3.put("uuu","red");
    map2.put("read", map3);
    map.put("io", map2);
    dump(prefix, result, map, "");
    return result;
  }

  public void dump(
      String prefix, List<CompletionItem> results, HashMap<String, Object> map, String sign) {
    for (String key : map.keySet()) {

      if ((sign + key).startsWith(prefix)) {
        // System.out.println(pre + "_" + key);
        results.add(
            new SimpleCompletionItem(key, map.get(key).toString() , prefix.length(), sign + key)
                .kind(CompletionItemKind.Function));
        // System.out.println(sign+key+".");

      } else if (prefix.startsWith(sign + key + ".")) {
        if (map.get(key) instanceof HashMap) {
          // System.out.print(key + ".");
          dump(prefix, results, (HashMap<String, Object>) map.get(key), sign + key + ".");
        }
      }
    }
  }

  /**
   * Make completion items for the given arguments. Provide the required arguments passed by {@link
   * Language#requireAutoComplete(ContentReference, CharPosition, CompletionPublisher, Bundle)}
   *
   * @param prefix The prefix to make completions for.
   */
  @Deprecated
  public void requireAutoComplete(
      @NonNull String prefix,
      @NonNull CompletionPublisher publisher,
      @Nullable Identifiers userIdentifiers) {
    publisher.setComparator(COMPARATOR);
    publisher.setUpdateThreshold(0);
    publisher.addItems(createCompletionItemList(prefix, userIdentifiers));
  }

  /**
   * Interface for saving identifiers
   *
   * @author Rosemoe
   * @see IdentifierAutoComplete.DisposableIdentifiers
   */
  public interface Identifiers {

    /**
     * Filter identifiers with the given prefix
     *
     * @param prefix The prefix to filter
     * @param dest Result list
     */
    void filterIdentifiers(@NonNull String prefix, @NonNull List<String> dest);
  }

  /**
   * This object is used only once. In other words, the object is generated every time the text
   * changes, and is abandoned when next time the text change.
   *
   * <p>In this case, the frequent allocation of memory is unavoidable. And also, this class is not
   * thread-safe.
   *
   * @author Rosemoe
   */
  public static class DisposableIdentifiers implements Identifiers {

    private static final Object SIGN = new Object();
    private final List<String> identifiers = new ArrayList<>(128);
    private HashMap<String, Object> cache;

    public void addIdentifier(String identifier) {
      if (cache == null) {
        throw new IllegalStateException("begin() has not been called");
      }
      if (cache.put(identifier, SIGN) == SIGN) {
        return;
      }
      identifiers.add(identifier);
    }

    /** Start building the identifiers */
    public void beginBuilding() {
      cache = new HashMap<>();
    }

    /** Free memory and finish building */
    public void finishBuilding() {
      cache.clear();
      cache = null;
    }

    @Override
    public void filterIdentifiers(@NonNull String prefix, @NonNull List<String> dest) {
      for (String identifier : identifiers) {
        var fuzzyScore =
            Filters.fuzzyScoreGracefulAggressive(
                prefix,
                prefix.toLowerCase(Locale.ROOT),
                0,
                identifier,
                identifier.toLowerCase(Locale.ROOT),
                0,
                FuzzyScoreOptions.getDefault());

        var score = fuzzyScore == null ? -100 : fuzzyScore.getScore();

        if ((TextUtils.startsWith(identifier, prefix, true) || score >= -20)
            && !(prefix.length() == identifier.length()
                && TextUtils.startsWith(prefix, identifier, false))) {
          dest.add(identifier);
        }
      }
    }
  }

  public static class SyncIdentifiers implements Identifiers {

    private final Lock lock = new ReentrantLock(true);
    private final Map<String, MutableInt> identifierMap = new HashMap<>();

    public void clear() {
      lock.lock();
      try {
        identifierMap.clear();
      } finally {
        lock.unlock();
      }
    }

    public void identifierIncrease(@NonNull String identifier) {
      lock.lock();
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          identifierMap.computeIfAbsent(identifier, (x) -> new MutableInt(0)).increase();
        } else {
          var counter = identifierMap.get(identifier);
          if (counter == null) {
            counter = new MutableInt(0);
            identifierMap.put(identifier, counter);
          }
          counter.increase();
        }
      } finally {
        lock.unlock();
      }
    }

    public void identifierDecrease(@NonNull String identifier) {
      lock.lock();
      try {
        var count = identifierMap.get(identifier);
        if (count != null) {
          if (count.decreaseAndGet() <= 0) {
            identifierMap.remove(identifier);
          }
        }
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void filterIdentifiers(@NonNull String prefix, @NonNull List<String> dest) {
      filterIdentifiers(prefix, dest, false);
    }

    public void filterIdentifiers(
        @NonNull String prefix, @NonNull List<String> dest, boolean waitForLock) {
      boolean acquired;
      if (waitForLock) {
        lock.lock();
        acquired = true;
      } else {
        try {
          acquired = lock.tryLock(3, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          acquired = false;
        }
      }
      if (acquired) {
        try {
          for (String s : identifierMap.keySet()) {
            var fuzzyScore =
                Filters.fuzzyScoreGracefulAggressive(
                    prefix,
                    prefix.toLowerCase(Locale.ROOT),
                    0,
                    s,
                    s.toLowerCase(Locale.ROOT),
                    0,
                    FuzzyScoreOptions.getDefault());

            var score = fuzzyScore == null ? -100 : fuzzyScore.getScore();

            if ((TextUtils.startsWith(s, prefix, true) || score >= -20)
                && !(prefix.length() == s.length() && TextUtils.startsWith(prefix, s, false))) {
              dest.add(s);
            }
          }
        } finally {
          lock.unlock();
        }
      }
    }
  }
}
