package de.ark.accounts;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Pattern;

final class SecretCommands implements AutoCloseable {
    private static final Pattern PRIVATE=Pattern.compile("(?i).*issued server command: /(?:[a-z0-9_.-]+:)?(?:(?:account|support|changepassword|changepass|cp|password|register|login|log|l|reg|unregister|unreg)\\s+|authme\\s+(?:password|changepassword|changepass|cp|register|reg|r)\\s+).*",Pattern.DOTALL);
    private final List<Object> loggers=new ArrayList<>();
    private Object filter;
    private Class<?> filterType;
    static boolean sensitive(String message){return message!=null && PRIVATE.matcher(message).matches();}
    void install() throws ReflectiveOperationException {
        filterType=Class.forName("org.apache.logging.log4j.core.Filter");
        Class<?> eventType=Class.forName("org.apache.logging.log4j.core.LogEvent"),messageType=Class.forName("org.apache.logging.log4j.message.Message");
        Method getMessage=eventType.getMethod("getMessage"),formatted=messageType.getMethod("getFormattedMessage");
        filter=Proxy.newProxyInstance(filterType.getClassLoader(),new Class<?>[]{filterType},(proxy,method,args)->{
            return switch(method.getName()) {
                case "filter" -> {
                    boolean denied=false;
                    if(args!=null && args.length==1 && eventType.isInstance(args[0]))denied=sensitive((String)formatted.invoke(getMessage.invoke(args[0])));
                    yield enumValue(method.getReturnType(),denied?"DENY":"NEUTRAL");
                }
                case "getOnMatch" -> enumValue(method.getReturnType(),"DENY");
                case "getOnMismatch" -> enumValue(method.getReturnType(),"NEUTRAL");
                case "getState" -> enumValue(method.getReturnType(),"STARTED");
                case "isStarted" -> true;
                case "isStopped" -> false;
                case "toString" -> "ArkAccounts sensitive command filter";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                case "stop" -> method.getReturnType()==boolean.class?true:null;
                default -> null;
            };
        });
        Object context=Class.forName("org.apache.logging.log4j.LogManager").getMethod("getContext",boolean.class).invoke(null,false);
        Object configuration=context.getClass().getMethod("getConfiguration").invoke(context);
        loggers.add(configuration.getClass().getMethod("getRootLogger").invoke(configuration));
        loggers.addAll(((Map<?,?>)configuration.getClass().getMethod("getLoggers").invoke(configuration)).values());
        for(Object logger:new HashSet<>(loggers))logger.getClass().getMethod("addFilter",filterType).invoke(logger,filter);
    }
    private static Object enumValue(Class<?> type,String name){for(Object value:type.getEnumConstants())if(((Enum<?>)value).name().equals(name))return value;throw new IllegalArgumentException();}
    public void close(){for(Object logger:new HashSet<>(loggers))try {logger.getClass().getMethod("removeFilter",filterType).invoke(logger,filter);}catch(Exception ignored){ }loggers.clear();}
}
