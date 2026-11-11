/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

/** Native-pair qualification only; full symbolic-cache topology admission remains unsupported. */
public final class NativeDependentChainStorageRunner {
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("dependent-chain-native-pair-").toString());
        for(boolean conflict:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})NativeDependentChainCollisionRunner.storage(conflict,reverse);
        System.out.println("NATIVE_DEPENDENT_CHAIN_STORAGE_PASS checks="+NativeDependentChainCollisionRunner.checks);
    }
}
