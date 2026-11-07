package de.ark.accounts;

public final class SecretCommandTest {
    public static void main(String[] args) {
        for(String value:new String[]{"Alice issued server command: /account secret-password","Alice issued server command: /arkwardrobe:account secret-password","Alice issued server command: /changepassword old new","Alice issued server command: /authme:changepassword old new",
            "Alice issued server command: /log secret-password","Alice issued server command: /AUTHME:LOG secret-password",
            "Alice issued server command: /unregister secret-password","Alice issued server command: /authme:unreg secret-password",
            "Alice issued server command: /authme register Bob secret-password","Alice issued server command: /authme:authme reg Bob secret-password","Alice issued server command: /AuThMe R Bob secret-password"})TestSupport.check(SecretCommands.sensitive(value),"sensitive command suppressed before preprocess: "+value.split(" /",2)[1].split(" ",2)[0]);
        TestSupport.check(!SecretCommands.sensitive("Alice issued server command: /account"),"safe dialog command logged normally");
        TestSupport.check(!SecretCommands.sensitive("Alice issued server command: /wardrobe wear My Skin"),"ordinary command unaffected");
        System.out.println("SecretCommandTest: "+TestSupport.checks+" checks passed");
    }
}
