package com.cloudbees.jenkins.clojure;

import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.Hudson;
import hudson.model.RootAction;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

@Extension
public class ClojureConsole extends AbstractModelObject implements RootAction {
    public String getIconFileName() {
        return "notepad.png";
    }

    public String getDisplayName() {
        return "Clojure Console";
    }

    public String getUrlName() {
        return "clojure-console";
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public void doScript(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doScript(req, rsp, req.getView(this, "index.jelly"));
    }

    private void doScript(StaplerRequest req, StaplerResponse rsp, RequestDispatcher view) throws IOException, ServletException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        String text = req.getParameter("script");
        if (text != null) {
            try {
                req.setAttribute("output",
                        executeClojure(text, Hudson.MasterComputer.localChannel));
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }

        view.forward(req, rsp);
    }

    private String executeClojure(String script, VirtualChannel channel) throws IOException, InterruptedException {
        return channel.call(new Script(script));
    }

    private static final class Script implements DelegatingCallable<String, RuntimeException> {
        private final String script;
        private transient ClassLoader cl;

        private Script(String script) {
            this.script = script;
            cl = getClassLoader();
        }

        public ClassLoader getClassLoader() {
            return Hudson.getInstance().getPluginManager().uberClassLoader;
        }

        public String call() throws RuntimeException {
            final ClassLoader old = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClassLoader());

                final ScriptEngineManager factory = new ScriptEngineManager(getClassLoader());
                final ScriptEngine engine = factory.getEngineByName("Clojure");

                final StringWriter out = new StringWriter();
                final PrintWriter pw = new PrintWriter(out);
                final ScriptContext c = engine.getContext();
                c.setErrorWriter(pw);
                c.setWriter(pw);

                engine.put("j", Jenkins.getInstance());
                engine.put("h", Hudson.getInstance());

                try {
                    final Object output = engine.eval(script);
                    if (output != null)
                        pw.println("Result: " + output);
                } catch (ScriptException e) {
                    e.printStackTrace(pw);
                }
                return out.toString();
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
    }
}