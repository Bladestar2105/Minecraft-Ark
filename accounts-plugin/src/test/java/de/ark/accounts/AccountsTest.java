package de.ark.accounts;

import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class AccountsTest {
    public static void main(String[] args) throws Exception {
        var file=Files.createTempDirectory("wardrobe-accounts").resolve("accounts.json");
        var sessions=new Sessions(file.resolveSibling("sessions.json"),Instant::now);
        var accounts=new Accounts(file,sessions,null);UUID owner=UUID.randomUUID();
        accounts.observe(owner,"Alice");
        TestSupport.check(!accounts.status(owner).get("password_configured").getAsBoolean(),"first password unset");
        TestSupport.denied(403,()->accounts.password(owner,"","a safe password123","a safe password123",false));
        accounts.password(owner,"","a safe password123","a safe password123",true);
        TestSupport.denied(401,()->accounts.login("Alice","wrong"));
        var login=accounts.login("aLiCe","a safe password123");String token=login.get("token").getAsString();
        TestSupport.check(sessions.require(token).uuid().equals(owner),"password login canonical UUID");
        TestSupport.denied(400,()->accounts.password(owner,"a safe password123","tiny","tiny",false));
        TestSupport.denied(400,()->accounts.password(owner,"a safe password123","a safe password123","different password",false));
        var codes=accounts.recovery(owner,"a safe password123",null,false).getAsJsonArray("codes");
        TestSupport.check(codes.size()==10,"ten recovery codes");
        String code=codes.get(0).getAsString();
        TestSupport.check(!Files.readString(file).contains(code),"recovery hashed only");
        TestSupport.denied(401,()->accounts.recovery(owner,"a safe password123",null,true));
        TestSupport.denied(401,()->accounts.recovery(owner,"wrong",code,true));
        accounts.recover("Alice",code,"my new password456","my new password456");
        TestSupport.denied(401,()->accounts.recover("Alice",code,"other password123","other password123"));
        TestSupport.denied(401,()->sessions.require(token));
        TestSupport.denied(401,()->accounts.login("Alice","a safe password123"));
        TestSupport.check(accounts.login("Alice","my new password456").has("token"),"recovery changes shared password");
        var restored=new Accounts(file,sessions,null);
        TestSupport.check(restored.status(owner).get("recovery_codes_remaining").getAsInt()==9,"recovery consumption persisted");
        var game=restored.login("Alice","my new password456").get("token").getAsString();
        restored.password(owner,"my new password456","final password123","final password123",false);
        TestSupport.denied(401,()->sessions.require(game));
        TestSupport.check(!Files.readString(file).contains("final password123"),"no plaintext passwords");
        UUID existing=UUID.randomUUID();
        var external=new Accounts(file.resolveSibling("external.json"),sessions,new Accounts.Authority(){
            public boolean configured(String name){return true;}
            public boolean check(String name,String password){return name.equalsIgnoreCase("Existing")&&password.equals("old-authme-password");}
            public Accounts.Identity identity(String name){return new Accounts.Identity(existing,"Existing");}
            public void change(String name,String password){throw new AssertionError("login must not change canonical AuthMe password");}
        });
        TestSupport.check(external.login("Existing","old-authme-password").get("uuid").getAsString().equals(existing.toString()),"existing AuthMe user signs in without prior pair or manifest");
        TestSupport.denied(401,()->external.login("Existing","wrong"));
        try {restored.recover("NotAPlayer","wrong-code","valid password123","valid password123");throw new AssertionError("unknown account recovery denied");}
        catch(ApiError e){TestSupport.check(e.code.equals("invalid_recovery_code"),"recovery errors do not enumerate accounts");}
        var sharedPassword=new java.util.concurrent.atomic.AtomicReference<>("unknown-generated-password");UUID premium=UUID.randomUUID();
        var bootstrap=new Accounts(file.resolveSibling("bootstrap.json"),sessions,new Accounts.Authority(){
            public boolean configured(String name){return true;}
            public boolean check(String name,String password){return sharedPassword.get().equals(password);}
            public Accounts.Identity identity(String name){return new Accounts.Identity(premium,"Premium");}
            public void change(String name,String password){sharedPassword.set(password);}
        });
        bootstrap.observe(premium,"Premium");bootstrap.recovery(premium,"unknown-generated-password",null,false);
        TestSupport.denied(401,()->bootstrap.password(premium,"","reset password123","reset password123",false));
        bootstrap.password(premium,"","reset password123","reset password123",true);
        TestSupport.check(sharedPassword.get().equals("reset password123"),"fresh game proof resets unknown configured AuthMe password");
        TestSupport.check(bootstrap.status(premium).get("recovery_codes_remaining").getAsInt()==10,"fresh game reset retains recovery codes");
        TestSupport.check(bootstrap.login("Premium","reset password123").get("token").getAsString().length()>32,"password login after game reset");
        var resetSessions=new Sessions(file.resolveSibling("admin-sessions.json"),Instant::now);var audit=new ArrayList<Accounts.Identity>();
        var adminAccounts=new Accounts(file.resolveSibling("admin-accounts.json"),resetSessions,null,audit::add);
        adminAccounts.observe(owner,"Alice");adminAccounts.password(owner,"","old admin password","old admin password",true);
        adminAccounts.recovery(owner,"old admin password",null,false);String lease=adminAccounts.login("Alice","old admin password").get("token").getAsString();
        adminAccounts.adminPassword("Alice","new admin password");
        TestSupport.denied(401,()->resetSessions.require(lease));
        TestSupport.check(adminAccounts.status(owner).get("recovery_codes_remaining").getAsInt()==10,"admin reset retains recovery");
        TestSupport.check(adminAccounts.login("Alice","new admin password").get("uuid").getAsString().equals(owner.toString()),"admin reset keeps canonical identity");
        TestSupport.check(audit.size()==1&&audit.getFirst().uuid().equals(owner)&&!audit.toString().contains("password"),"admin audit contains target identity only");
        TestSupport.denied(404,()->adminAccounts.adminPassword("Missing","new admin password"));
        TestSupport.check(!adminAccounts.list().toString().contains("recovery")&&!adminAccounts.list().toString().contains("password"),"account listing excludes secrets");
        System.out.println("AccountsTest: "+TestSupport.checks+" checks passed");
    }
}
