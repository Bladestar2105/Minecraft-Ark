package de.ark.accounts;
final class TestSupport {static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}static void denied(int status,Runnable action){try{action.run();throw new AssertionError("expected "+status);}catch(de.ark.accounts.api.AccountsApi.Failure e){check(e.status==status,"wrong status "+e.status);}}}
