package org.mozilla.javascript;

import java.io.Closeable;
import java.util.Iterator;

/**
 * This is a class that makes it easier to iterate over "iterator-like" objects as defined
 * in the ECMAScript spec. The caller is responsible for retrieving an object that implements
 * the "iterator" pattern. This class will follow that pattern and throw appropriate
 * JavaScript exceptions.
 * <p>
 * The pattern that the target class should follow is:
 * * It should have a function property called "next"
 * * The property should return an object with a boolean value called "done".
 * * If "done" is true, then the returned object should also contain a "value" property.
 * * If it has a function property called "return" then it will be called
 * when the caller is done iterating.
 */
public class IteratorLikeIterable implements Iterable<Object>, Closeable {
    private final Context cx;
    private final Scriptable scope;
    private final Callable next;
    private final Callable returnFunc;
    private final Scriptable iterator;
    private boolean closed;

    public IteratorLikeIterable(Context cx, Scriptable scope, Object target) {
        this.cx = cx;
        this.scope = scope;
        next = ScriptRuntime.getPropFunctionAndThis(target, "next", cx, scope);
        iterator = ScriptRuntime.lastStoredScriptable(cx);
        Scriptable st = ScriptableObject.ensureScriptable(target);
        if (st.has("return", st) && !ScriptRuntime.isNullOrUndefined(ScriptableObject.getProperty(st, "return"))) {
            returnFunc = ScriptRuntime.getPropFunctionAndThis(target, "return", cx, scope);
            ScriptRuntime.lastStoredScriptable(cx);
        } else {
            returnFunc = null;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (returnFunc != null) {
                returnFunc.call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            }
        }
    }

    @Override
    public Itr iterator() {
        return new Itr();
    }

    public final class Itr implements Iterator<Object> {
        private Object nextVal;

        @Override
        public boolean hasNext() {
            final Object val = next.call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            Object doneval = ScriptRuntime.getObjectProp(val, "done", cx, scope, false);
            if (Undefined.instance.equals(doneval)) {
                doneval = false;
            }
            if (ScriptRuntime.toBoolean(doneval)) {
                return false;
            }
            nextVal = ScriptRuntime.getObjectProp(val, "value", cx, scope, false);
            return true;
        }

        @Override
        public Object next() {
            return nextVal;
        }
    }
}
