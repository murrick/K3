/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.test;

import org.kanger.Mind;
import org.kanger.enums.FunctionBinding;
import org.kanger.enums.LibMode;
import org.kanger.factory.LibraryFactory;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.Function;
import org.kanger.units.Operation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Regression corpus for C3 compiled function binding.
 */
public final class KangerC3BindingTest {

    private IMind mind;

    private KangerC3BindingTest(IMind mind) {
        this.mind = mind;
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        KangerC3BindingTest suite = new KangerC3BindingTest(mind);
        Map<String, Method> methods = new TreeMap<>();
        for (Method method : KangerC3BindingTest.class.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix) && method.getParameterTypes().length == 0) {
                method.setAccessible(true);
                methods.put(method.getName(), method);
            }
        }

        int success = 0;
        int failures = 0;
        long start = System.currentTimeMillis();
        System.out.println("====================================================");
        System.out.println("C3 function binding regression tests");

        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            System.out.println("Testing: " + entry.getKey());
            try {
                entry.getValue().invoke(suite);
                ++success;
                System.out.println("OK");
            } catch (InvocationTargetException error) {
                ++failures;
                Throwable cause = error.getCause() == null ? error : error.getCause();
                cause.printStackTrace(System.err);
            }
            System.out.println("----------------------------------------------------");
        }

        System.out.println("C3 Timing: " + ((System.currentTimeMillis() - start) / 1000.0));
        System.out.println("C3 Success: " + success);
        System.out.println("C3 Fails: " + failures);
        System.out.println("====================================================");
        return failures == 0;
    }

    private void resetWorkspace() throws Exception {
        mind = mind.clearWorkspace();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private Operation constantOperation(final String name, final int range, final double value) {
        Operation operation = new Operation(LibMode.FUNCTION, name, range, new IReactor<Function>() {
            @Override
            public Object run(Function function) throws Exception {
                Mind context = function.getMind();
                ITerm expected = context.getTerms().add(value);
                IArgument result = function.getArguments().get(function.getRange());
                if (result.isEmpty(context)) {
                    return function.setParameter(function.getRange(), expected) ? 1 : 0;
                }
                return result.getValue(context).getId() == expected.getId() ? 2 : 0;
            }
        });
        for (int i = 0; i < range; ++i) {
            operation.getParams().add("p" + i);
        }
        operation.getParams().add(name);
        operation.getScripts().add(name + " = " + value + ";");
        return operation;
    }

    private void installUdf(String name, int range, double value) throws Exception {
        ((LibraryFactory) mind.getLibrary()).add(constantOperation(name, range, value));
    }

    private void installInfrastructure(String name, int range, double value) {
        ((Mind) mind).getCalculator().getFunctions().getSysOps()
                .put(name + "(" + range + ")", constantOperation(name, range, value));
    }

    private void deleteUdf(String name, int range) throws Exception {
        Operation operation = ((LibraryFactory) mind.getLibrary()).find(name + "(" + range + ")");
        require(operation != null, "UDF is missing before deletion: " + name);
        operation.setDeleted(true, (Mind) mind);
        ((LibraryFactory) mind.getLibrary()).pack();
        require(((LibraryFactory) mind.getLibrary()).find(name + "(" + range + ")") == null,
                "Deleted UDF is still resolvable: " + name);
    }

    private List<Function> functions(String name) throws Exception {
        List<Function> result = new ArrayList<>();
        for (Object value : ((Mind) mind).getFunctions()) {
            Function function = (Function) value;
            if (name.equals(function.getName((Mind) mind).toString())) {
                result.add(function);
            }
        }
        return result;
    }

    private Function requireFunction(String name, FunctionBinding binding) throws Exception {
        for (Function function : functions(name)) {
            if (function.getBinding() == binding) {
                return function;
            }
        }
        throw new AssertionError("Function binding not found: " + name + " / " + binding);
    }

    private void requireTrue(String query, String message) throws Exception {
        require(Boolean.TRUE.equals(mind.query(query)), message + ": " + query);
    }

    @SuppressWarnings("unchecked")
    public void set_c3_01_infrastructure_binding_precedence() throws Exception {
        resetWorkspace();
        installUdf("_add", 2, 999.0);

        requireTrue("?$x x=1+2;", "Infrastructure addition was not selected");
        Map<String, ITerm> row = (Map<String, ITerm>) mind.getValues().iterator().next();
        require(Double.valueOf(3.0).equals(row.get("x").getValue()),
                "UDF intercepted the infrastructure addition occurrence");
        requireFunction("_add", FunctionBinding.INFRASTRUCTURE);
    }

    public void set_c3_02_dynamic_udf_redefinition() throws Exception {
        resetWorkspace();
        installUdf("c3_redefine", 1, 10.0);

        requireTrue("!@x c3_redefine_source(x) -> c3_redefine_result(c3_redefine(x));",
                "Dynamic UDF rule was not accepted");
        requireTrue("!c3_redefine_source(value);", "Dynamic UDF source was not accepted");
        requireTrue("?c3_redefine_result(10);", "Initial dynamic UDF result is missing");
        requireFunction("c3_redefine", FunctionBinding.UDF_DYNAMIC);

        installUdf("c3_redefine", 1, 20.0);
        requireTrue("?", "Reinitialization after UDF redefine failed");
        requireTrue("?c3_redefine_result(20);", "Compiled dynamic occurrence did not use redefined UDF");
    }

    public void set_c3_03_dynamic_udf_deletion() throws Exception {
        resetWorkspace();
        installUdf("c3_delete", 1, 10.0);

        requireTrue("!@x c3_delete_source(x) -> c3_delete_result(c3_delete(x));",
                "Dynamic deletion rule was not accepted");
        requireTrue("!c3_delete_source(value);", "Dynamic deletion source was not accepted");
        requireTrue("?c3_delete_result(10);", "Initial dynamic deletion result is missing");
        requireFunction("c3_delete", FunctionBinding.UDF_DYNAMIC);

        deleteUdf("c3_delete", 1);
        requireTrue("?", "Reinitialization after UDF deletion failed");
        Boolean result = mind.query("?c3_delete_result(10);");
        require(result == null, "Deleted UDF still produced a result: " + result);
    }

    public void set_c3_04_late_infrastructure_does_not_capture_dynamic() throws Exception {
        resetWorkspace();
        installUdf("c3_late", 1, 10.0);

        requireTrue("!@x c3_old_source(x) -> c3_old_result(c3_late(x));",
                "Old dynamic rule was not accepted");
        requireTrue("!c3_old_source(value);", "Old dynamic source was not accepted");
        requireTrue("?c3_old_result(10);", "Old dynamic result is missing");
        requireFunction("c3_late", FunctionBinding.UDF_DYNAMIC);

        installInfrastructure("c3_late", 1, 99.0);
        requireTrue("?", "Reinitialization after infrastructure registration failed");
        requireTrue("?c3_old_result(10);", "Late infrastructure captured an existing dynamic occurrence");

        requireTrue("!@x c3_new_source(x) -> c3_new_result(c3_late(x));",
                "New infrastructure-bound rule was not accepted");
        requireTrue("!c3_new_source(value);", "New infrastructure source was not accepted");
        requireTrue("?c3_new_result(99);", "New occurrence was not bound to infrastructure");
        requireFunction("c3_late", FunctionBinding.INFRASTRUCTURE);
    }

    public void set_c3_05_binding_persistence_reopen() throws Exception {
        resetWorkspace();
        final String storageName = "data/c3-binding-persistence";

        try {
            if (mind.isStorageUsed()) {
                mind = mind.closeStorage();
            }
            if (mind.isStorageExists(storageName)) {
                mind = mind.removeStorage(storageName);
            }

            mind = mind.useStorage(storageName);
            mind = mind.clearWorkspace();
            installUdf("c3_persist", 1, 10.0);

            requireTrue("!@x c3_persist_source(x) -> c3_persist_result(c3_persist(x));",
                    "Persistent dynamic rule was not accepted");
            Function before = requireFunction("c3_persist", FunctionBinding.UDF_DYNAMIC);
            long functionId = before.getId();

            mind = mind.closeStorage();
            mind = mind.useStorage(storageName);

            Function loaded = ((Mind) mind).getFunctions().get(functionId);
            require(loaded != null, "Persisted Function is missing after reopen");
            require(loaded.getBinding() == FunctionBinding.UDF_DYNAMIC,
                    "Persisted binding changed after reopen: " + loaded.getBinding());
        } finally {
            if (mind.isStorageUsed()) {
                mind = mind.closeStorage();
            }
            if (mind.isStorageExists(storageName)) {
                mind = mind.removeStorage(storageName);
            }
        }
    }

    public void set_c3_06_legacy_packet_defaults_to_auto() throws Exception {
        resetWorkspace();
        requireTrue("?$x x=abs(-2);", "Infrastructure function query failed");
        Function source = requireFunction("abs", FunctionBinding.INFRASTRUCTURE);

        ByteBuffer legacyPacket = new ByteBuffer()
                .putLong(source.getId())
                .putLong(source.getMindId())
                .putByte(0)
                .putLong(source.getNameId())
                .putInt(source.getRange())
                .append(source.getArguments().pack())
                .createMarked();

        Function restored = new Function((Mind) mind);
        try {
            legacyPacket.mark();
            restored.apply(legacyPacket);
        } finally {
            legacyPacket.release();
        }
        require(restored.getBinding() == FunctionBinding.LEGACY_AUTO,
                "Legacy packet did not default to LEGACY_AUTO");
    }
}
