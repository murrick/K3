/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger;

import org.json.JSONException;
import org.json.JSONObject;
import org.kanger.compiler.Token;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.enums.Tools;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.ParseErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.*;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Hypothesis;
import org.kanger.primitives.LogEntry;
import org.kanger.storage.DB;
import org.kanger.stores.HypothesisStore;
import org.kanger.udf.UDF;
import org.kanger.units.Domain;
import org.kanger.units.Operation;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class QueryProcessor implements IReactor<JSONObject> {

    public static void sendEmail(String[] addressTo,
                                 String subject,
                                 String text,
                                 String from,
                                 String charset,
                                 String login,
                                 String password,
                                 String host,
                                 String port) throws Exception {

        try {

            if (addressTo.length == 0 || addressTo[0].length() == 0) {
                throw new AddressException("Receiver address not defined");
            }
            Properties props = new Properties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.debug", "true");
            props.put("mail.smtp.ssl.enable", "true");

            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);

            Session session = Session.getInstance(props,
                    new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(login, password);
                        }
                    });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from));
            for (String to : addressTo) {
                try {
                    new InternetAddress(to).validate();
                    msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
                } catch (AddressException e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
            }
            msg.setSentDate(new Date());
            msg.addHeader("Reply-To", from);

            msg.setSubject(subject, charset);
            msg.setContent(text, "text/plain; charset=" + charset);
            Transport.send(msg, login, password);
        } catch (MessagingException e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            throw new Exception(e);
        }
    }

    private JSONObject processHelp() {
        JSONObject result = new JSONObject();
        result.put("result", "OK");
        result.put("description", "<b>Console commands</b>:<br>" +
                "\t<b>use [name]</b> - use database<br>" +
                "\t<b>used</b> - show information about used database<br>" +
                "\t<b>close</b> - close database<br>" +
                "\t<b>drop [name]</b> - drop database<br>" +
                "\t<b>reindex [name]</b> - optimize and reindex database<br>" +
                "\t<b>get [name]</b> - open source file from repository<br>" +
                "\t<b>put [name]</b> - save source file to repository<br>" +
                "\t<b>delete [name]</b> - delete source file from repository<br>" +
                "\t<b>erase</b> - clear workspace<br>" +
                "\t<b>transaction</b> - show transaction level<br>" +
                "\t<b>transaction create</b> or <b>create</b> - create new transaction<br>" +
                "\t<b>transaction commit</b> or <b>commit</b> - commit current transaction<br>" +
                "\t<b>transaction rollback</b> or <b>rollback</b> - rollback current transaction<br>" +
                "\t<b>help</b> - show this message<br>" +
                "\t<b>quit</b> - log out<br>"
        );
        return result;
    }

    static final String EMAIL_CONFIRMED_PROPERTY = "reg.email.confirmed";

    static boolean isLegacyRootConfirmation(String context, JSONObject parameters) {
        return context != null
                && context.isEmpty()
                && parameters != null
                && parameters.has("confirm")
                && !parameters.isNull("confirm")
                && !parameters.optString("confirm", "").trim().isEmpty();
    }

    private static boolean isEmailConfirmed(IUser user) throws Exception {
        String legacy = user.getProperty("reg.agreed", false + "");
        return Boolean.parseBoolean(user.getProperty(
                EMAIL_CONFIRMED_PROPERTY, legacy));
    }

    @Override
    public Object run(JSONObject o) throws Exception {
        JSONObject result = null;
        String context = null;
        JSONObject parameters = null;
        try {
            context = o.getJSONObject("body").getString("context");
            parameters = o.getJSONObject("body").getJSONObject("parameters");
        } catch (JSONException ex) {
            try {
                context = o.getJSONObject("query").getString("context");
                parameters = o.getJSONObject("query").getJSONObject("parameters");
            } catch (JSONException exx) {
                result = new JSONObject();
                result.put("result", "error");
                result.put("description", exx.toString());
                exx.printStackTrace(System.err);
            }
        }
        if (context != null) {
            try {
                if ("login".equalsIgnoreCase(context)
                        || isLegacyRootConfirmation(context, parameters)) {
                    result = processLogin(parameters);
                } else if ("version".equalsIgnoreCase(context)) {
                    result = new JSONObject();
                    result.put("result", "OK");
                    result.put("version", Version.VERSION_S);
                } else if (!parameters.isNull("token")) {
                    String token = parameters.getString("token");
                    IUser user = UserFactory.getUser(token);
                    if (user.getCurrentMind() == null) {
                        IMind mind = new Mind(user);
                        user.setCurrentMind(mind);
                    }

                    if ("command".equalsIgnoreCase(context)) {
                        result = processCommand(parameters, user);
                    } else if ("query".equalsIgnoreCase(context)) {
                        result = processQuery(parameters, user);
                    } else if ("history".equalsIgnoreCase(context)) {
                        result = processHistory(parameters, user);
                    }
                } else {
                    throw new AuthenticationErrorException("User not logged in");
                }
            } catch (ParseErrorException failure) {
                throw failure;
            } catch (AuthenticationErrorException failure) {
                throw failure;
            } catch (Exception e) {
                result = new JSONObject();
                result.put("result", "error");
                result.put("description", e.toString());
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        }
        return result;
    }

    private JSONObject processHistory(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();
        List<String> list = new ArrayList<>();
        if (!parameters.isNull("get")) {
            for (String s : UserFactory.getHistory(user)) {
                list.add(URLEncoder.encode(s, "utf-8"));
            }
            result.put("result", "OK");
            result.put("size", list.size());
            result.put("list", list);
        } else if (!parameters.isNull("put")) {
            String s = URLDecoder.decode(parameters.getString("put"), "utf-8");
            UserFactory.addHistory(user, s);
            result.put("result", "OK");
        }
        if (result != null) {
            result.put("transaction", mind.getTransactionLevel());
            result.put("empty", mind.isEmptyLevel());
        }
        return result;
    }

    private JSONObject processQuery(JSONObject parameters, IUser user) throws Exception {
        JSONObject result = new JSONObject();

        if (!parameters.isNull("request")) {
            result = query(parameters, user);
        } else if (!parameters.isNull("compile")) {
            result = compile(parameters, user);
        } else if (!parameters.isNull("results")) {
            result = getValues(user);
        } else if (!parameters.isNull("solutions")) {
            result = getSolutions(parameters, user);
        } else if (!parameters.isNull("hypothesis")) {
            result = getHypothesis(parameters, user);
        } else if (!parameters.isNull("rules")) {
            result = getRules(parameters, user);
        } else if (!parameters.isNull("predicates")) {
            result = getPredicates(parameters, user);
        } else if (!parameters.isNull("statements")) {
            result = getStatements(parameters, user);
        } else if (!parameters.isNull("functions")) {
            result = getFunctions(parameters, user);
        } else if (!parameters.isNull("transaction")) {
            result = processTransaction(parameters, user);
        } else if (!parameters.isNull("log")) {
            result = getLog(parameters, user);
        } else if (!parameters.isNull("source")) {
            result = getSourceCode(user);
        }
        if (result != null) {
            IMind mind = user.getCurrentMind();
            result.put("transaction", mind.getTransactionLevel());
            result.put("empty", mind.isEmptyLevel());
        }
        return result;
    }

    private JSONObject getSourceCode(IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();
        result.put("result", "OK");
        result.put("source", SourceContextMaterializer.materializeCurrentLevel(mind));
        return result;
    }

    private JSONObject getLog(JSONObject parameters, IUser user) {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();
        LogMode filter = LogMode.ALL;
        if (!parameters.getString("log").isEmpty()) {
            filter = LogMode.valueOf(parameters.getString("log"));
        }
        List<JSONObject> list = new ArrayList<>();
        for (ILogEntry log : mind.getLog()) {
            if (filter == LogMode.ALL || filter == log.getType()) {
                list.add(new JSONObject(((LogEntry) log).createMap()));
            }
        }
        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    private JSONObject processTransaction(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        if ("create".equalsIgnoreCase(parameters.getString("transaction"))) {
            IMind m = new Mind(mind);
            user.setCurrentMind(m);
            result.put("result", "OK");
            result.put("description", "New transaction created");
        } else if ("commit".equalsIgnoreCase(parameters.getString("transaction"))) {
            IMind m = mind.getNext();
            if (m != null) {
                if (m.commit(mind)) {
                    result.put("result", "OK");
                } else {
                    result.put("result", "error");
                }
                user.setCurrentMind(m);
                if (!m.getLog().isEmpty()) {
                    result.put("description", m.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
                } else {
                    result.put("description", "Transaction committed");
                }
            } else {
                result.put("result", "error");
                result.put("description", "No transactions was created");
            }
        } else if ("rollback".equalsIgnoreCase(parameters.getString("transaction"))) {
            IMind m = mind.getNext();
            if (m != null) {
                m.release(mind);
                user.setCurrentMind(m);
                result.put("result", "OK");
                if (!m.getLog().isEmpty()) {
                    result.put("description", m.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
                } else {
                    result.put("description", "Transaction rolled back");
                }
            } else {
                result.put("result", "error");
                result.put("description", "No transactions was created");
            }
        }
        if (result != null) {
            result.put("id", mind.getId());
            result.put("transaction", mind.getTransactionLevel());
            result.put("empty", mind.isEmptyLevel());
        }
        return result;
    }

    private JSONObject compile(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        String source = URLDecoder.decode(parameters.getString("compile"), "utf-8");
        mind = mind.clearWorkspace();
        boolean res = mind.compile(source);
        result.put("result", res ? "OK" : "error");
        result.put("description", mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
        return result;
    }

    private JSONObject query(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();
        mind.clearLog();
        ((HypothesisStore) mind.getHypothesis()).clear();
        String query = parameters.getString("request");
        query = URLDecoder.decode(query, "utf-8");
        Token t = null;

        Mind m = new Mind(mind);
        boolean succ = false;
        Boolean res = null;
        while ((t = Tools.extractLine(query, t)) != null) {
            if (t.getToken(query).charAt(0) == '?') {
                succ = true;
            }

            res = m.query(t.getToken(query));
        }
        if (res != null) {

        }
        if (!succ) {
            mind.commit(m);
        } else {
            mind.release(m);
            if(res == null) {
                ((HypothesisStore) mind.getHypothesis()).commit(m.getHypothesis());
            }
        }

        if (res == null) {
            result.put("response", "unknown");
        } else {
            result.put("response", res ? "yes" : "no");
        }
        result.put("results", mind.getValues().size());
        result.put("solutions", mind.getSolutions().size());
        result.put("hypothesis", mind.getHypothesis().size());
        if (mind.getCurrentLogRecord(LogMode.ANALYZER) != null) {
            result.put("result", "OK");
            String description = mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord();
            for (ILogEntry e : mind.getLog()) {
                if (e.getType() == LogMode.SOLVES) {
                    description += "<br>" + e.getRecord();
                }
            }
            for (ILogEntry e : mind.getLog()) {
                if (e.getType() == LogMode.VALUES) {
                    description += "<br>" + e.getRecord();
                }
            }
            result.put("description", description);
        } else {
            result.put("result", "error");
            result.put("description", "Query error");
        }
        return result;
    }

    private JSONObject getSolutions(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        List<JSONObject> list = new ArrayList<>();
        for (IRule r : mind.getSolutions()) {
            JSONObject op = new JSONObject(((Rule) r).createMap(mind));
            list.add(op);
        }

        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    private JSONObject getValues(IUser user) {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        List<List<JSONObject>> list = new ArrayList<>();
        for (Map<String, ITerm> row : mind.getValues()) {
            List<JSONObject> one = new ArrayList<>();
            for (Map.Entry<String, ITerm> e : row.entrySet()) {
                JSONObject op = new JSONObject();
                op.put("name", e.getKey());
                op.put("value", e.getValue().getValue());
                one.add(op);
            }
            list.add(one);
        }

        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    private JSONObject processLogin(JSONObject parameters) throws Exception {
        JSONObject result = new JSONObject();
        if (!parameters.isNull("currentpassword") && !parameters.isNull("currentlogin")) {
            try {
                IUser user = UserFactory.getUser(parameters.getString("currentlogin"), parameters.getString("currentpassword"));
                UserFactory.dropUser(user);
                String login = parameters.getString("login");
                String password = parameters.getString("password");
                if (password.isEmpty()) {
                    password = parameters.getString("currentpassword");
                }
                UserFactory.updateUserToken(user, login, password);
                String token = UserFactory.addUser(user);
                user.setProperty("reg.login", login);

                result.put("result", "OK");
                result.put("token", token);
                result.put("login", login);
                Watchdog.log(user, "User login ot password changed (" + parameters.getString("currentlogin") + ")");
            } catch (Exception e) {
                result.put("result", "error");
                result.put("description", e.toString());
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        } else if (!parameters.isNull("login") && !parameters.isNull("password")) {
            try {
                IUser user = UserFactory.getUser(parameters.getString("login"), parameters.getString("password"));
                if (isEmailConfirmed(user)) {
                    try {
                        ((User) user).getData();
                    } catch (RuntimeErrorException e) {
                        new DB().init(user);
                    }
                    try {
                        ((User) user).getUdf();
                    } catch (RuntimeErrorException e) {
                        new UDF().init(user);
                    }
                }
                String token = UserFactory.addUser(user);
                result.put("result", "OK");
                result.put("token", token);
                Watchdog.log(user, "User logged in");
            } catch (AuthenticationErrorException failure) {
                throw failure;
            } catch (Exception e) {
                result.put("result", "error");
                result.put("description", e.toString());
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        } else if (!parameters.isNull("confirm")) {
            try {
                String userToken = parameters.getString("confirm");
                IUser user = UserFactory.getUserByToken(userToken);
                String email = user.getProperty("reg.email", "");
                boolean alreadyConfirmed = isEmailConfirmed(user);
                if (!alreadyConfirmed) {
                    user.setProperty("reg.agreed", true + "");
                    user.setProperty(EMAIL_CONFIRMED_PROPERTY, true + "");
                }
                result.put("result", "OK");
                result.put("description", "E-mail " + email
                        + (alreadyConfirmed ? " already confirmed" : " confirmed"));
                result.put("email_confirmed", true);
                Watchdog.log(user, "E-mail " + email
                        + (alreadyConfirmed ? " already confirmed" : " confirmed"));
            } catch (Exception e) {
                result.put("result", "error");
                result.put("description", e.toString());
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        } else if (!parameters.isNull("resend") && !parameters.isNull("token")) {
            try {
                IUser user = UserFactory.getUser(parameters.getString("token"));
                String userToken = UserFactory.getUserToken(user);

                if (!user.getProperty("reg.email", "").isEmpty()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                sendConfirmation(user, userToken);
                            } catch (Exception e) {
                                System.err.println(new Date());
                                e.printStackTrace(System.err);
                            }
                        }

                    }).start();
                    result.put("result", "OK");
                    result.put("description", "Sending e-mail to " + user.getProperty("reg.email", "") + " queued");
                } else {
                    result.put("result", "error");
                    result.put("description", "E-mail address not defined");
                }

            } catch (Exception e) {
                result.put("result", "error");
                result.put("description", e.toString());
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        } else if (!parameters.isNull("info") && !parameters.isNull("token")) {
            try {
                String token = parameters.getString("token");
                IUser user = UserFactory.getUser(token);

                result.put("login", user.getProperty("reg.login", ""));
                result.put("email", user.getProperty("reg.email", ""));
                result.put("name", URLEncoder.encode(user.getProperty("reg.name", ""), "utf-8"));
                result.put("country", URLEncoder.encode(user.getProperty("reg.country", ""), "utf-8"));
                result.put("city", URLEncoder.encode(user.getProperty("reg.city", ""), "utf-8"));

                result.put("result", "OK");
                result.put("token", token);

            } catch (AuthenticationErrorException failure) {
                throw failure;
            } catch (Exception e) {
                result.put("result", "error");
                result.put("description", e.toString());
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        } else if (!parameters.isNull("register") && !parameters.isNull("password")) {
            try {
                UserFactory.getUser(parameters.getString("register"), parameters.getString("password"));
                result.put("result", "error");
                result.put("description", "User " + parameters.getString("register") + " already registered");
            } catch (AuthenticationErrorException ex) {
                try {
                    String userToken;
                    IUser user;
                    String token = parameters.getString("token");
                    boolean newUser = true;
                    if (token.isEmpty()) {
                        userToken = UserFactory.token(parameters.getString("register"), parameters.getString("password"));
                        user = UserFactory.createUser(parameters.getString("register"), parameters.getString("password"));
                        user.setProperty("reg.agreed", false + "");
                        user.setProperty(EMAIL_CONFIRMED_PROPERTY, false + "");
                    } else {
                        newUser = false;
                        user = UserFactory.getUser(token);
                        userToken = UserFactory.getUserToken(user);
                    }

                    if (!parameters.isNull("register")) {
                        user.setProperty("reg.login", parameters.getString("register"));
                    }
                    if (!parameters.isNull("email")) {
                        user.setProperty("reg.email", parameters.getString("email"));
                    }
                    if (!parameters.isNull("name")) {
                        user.setProperty("reg.name", URLDecoder.decode(parameters.getString("name"), "utf-8"));
                    }
                    if (!parameters.isNull("country")) {
                        user.setProperty("reg.country", URLDecoder.decode(parameters.getString("country"), "utf-8"));
                    }
                    if (!parameters.isNull("city")) {
                        user.setProperty("reg.city", URLDecoder.decode(parameters.getString("city"), "utf-8"));
                    }
                    if (newUser && !parameters.isNull("privacy")) {
                        user.setProperty("reg.privacy", parameters.getBoolean("privacy") + "");
                    }
                    if (newUser) {
                        if (isEmailConfirmed(user)) {
                            try {
                                ((User) user).getData();
                            } catch (RuntimeErrorException e) {
                                new DB().init(user);
                            }
                            try {
                                ((User) user).getUdf();
                            } catch (RuntimeErrorException e) {
                                new UDF().init(user);
                            }
                        }
                        token = UserFactory.addUser(user);
                        Watchdog.log(user, "New user registered");
                    } else {
                        Watchdog.log(user, "User data updated");
                    }
                    result.put("result", "OK");
                    result.put("token", token);
                    result.put("login", user.getProperty("reg.login", ""));

                    if (newUser && !parameters.isNull("email") && !parameters.getString("email").isEmpty()) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    sendConfirmation(user, userToken);
                                } catch (Exception e) {
                                    System.err.println(new Date());
                                    e.printStackTrace(System.err);
                                }
                            }

                        }).start();
                    }

                } catch (Exception e) {
                    result.put("result", "error");
                    result.put("description", e.toString());
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
            } catch (Exception e) {
                result.put("result", "error");
                result.put("description", e.toString());
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        } else {
            result.put("result", "error");
            result.put("description", "Login request format error");
        }
        return result;
    }

    private void sendConfirmation(IUser user, String userToken) throws Exception {
        String email = user.getProperty("reg.email", "");
        String login = user.getProperty("reg.login", "");
        Watchdog.log(user, "Sending confirmation email to " + email);
        sendEmail(new String[]{email},
                "KANGER: Registration confirmation",
                "You are just registered on site kanger.org with login " +
                        login +
                        ". Please confirm your e-mail by following link: " +
                        Settings.getProperty("server.url", "https://kanger.org") +
                        "/login?confirm=" + userToken,
                Settings.getProperty("server.email.from", ""),
                "utf-8",
                Settings.getProperty("server.email.login", ""),
                Settings.getProperty("server.email.password", ""),
                Settings.getProperty("server.email.host", ""),
                Settings.getProperty("server.email.port", "465"));
        Watchdog.log(user, "Confirmation email to " + email + " sent");
    }

    private JSONObject processCommand(JSONObject parameters, IUser user) throws Exception {
        JSONObject result = null;
        if (!parameters.isNull("get")) {
            result = loadSourceFile(parameters, user);
        } else if (!parameters.isNull("put")) {
            result = saveSourceFile(parameters, user);
        } else if (!parameters.isNull("delete")) {
            result = deleteSourceFile(parameters, user);
        } else if (!parameters.isNull("use")) {
            result = useDatabase(parameters, user);
        } else if (!parameters.isNull("used")) {
            result = usedDatabase(user);
        } else if (!parameters.isNull("close")) {
            result = closeDatabase(user);
        } else if (!parameters.isNull("drop")) {
            result = dropDatabase(parameters, user);
        } else if (!parameters.isNull("reindex")) {
            result = reindexDatabase(parameters, user);
        } else if (!parameters.isNull("erase")) {
            result = clearWorkspace(user);
        } else if (!parameters.isNull("ping")) {
            result = pong(user);
        } else if (!parameters.isNull("help")) {
            result = processHelp();
        }
        if (result != null) {
            IMind mind = user.getCurrentMind();
            result.put("transaction", mind.getTransactionLevel());
            result.put("empty", mind.isEmptyLevel());
        }
        if (!parameters.isNull("quit")) {
            result = quit(user);
        }
        return result;
    }

    private JSONObject pong(IUser user) {
        JSONObject result = new JSONObject();
        result.put("result", "OK");
        result.put("description", "pong");
        return result;
    }

    private JSONObject deleteSourceFile(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        String fname = parameters.getString("delete");
        if (fname != null && !fname.isEmpty()) {
            File f = new File(mind.getUser().getSourceDir() + fname);
            if (f.exists()) {
                f.delete();
                result.put("result", "OK");
                result.put("description", "Source file " + fname + " deleted.");
                Watchdog.log(user, "Source file " + fname + " deleted.");
            } else {
                result.put("result", "error");
                result.put("description", "Source file not found " + fname);
            }
        } else {
            result.put("result", "error");
            result.put("description", "You have to select name for file");
        }
        return result;
    }

    private JSONObject reindexDatabase(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        String name = null;
        if (!parameters.getString("reindex").isEmpty()) {
            name = parameters.getString("reindex");
        }
        mind = mind.reindexStorage(name);
        user.setCurrentMind(mind);
        result.put("result", "OK");
        return result;
    }

    private JSONObject dropDatabase(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        String name = null;
        if (!parameters.getString("drop").isEmpty()) {
            name = parameters.getString("drop");
        }

        String current = mind.getStorageName();
        boolean exists = (mind.isStorageUsed() && name == null) || (name != null && mind.isStorageExists(name));
        mind = mind.removeStorage(name);
        if (exists) {
            Watchdog.log(user, "Database " + (name == null ? current : name) + " deleted");
        }
        user.setCurrentMind(mind);
        result.put("result", "OK");
        return result;
    }

    private JSONObject getHypothesis(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        mind.optimizeHypothesis();
        List<JSONObject> list = new ArrayList<>();
        for (IHypothesis h : mind.getHypothesis()) {
            JSONObject op = new JSONObject(((Hypothesis) h).createMap(mind));
            list.add(op);
        }

        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    private JSONObject getFunctions(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();
        boolean sources = false;
        long id = -1;

        if (!parameters.isNull("sources")) {
            sources = parameters.getBoolean("sources");
        }
        if (!parameters.isNull("id")) {
            id = parameters.getLong("id");
        }

        List<JSONObject> list = new ArrayList<>();
        for (IOperation o : mind.getLibrary()) {
            if (!o.isDeleted(mind) && (id == -1 || id == o.getId())) {
                JSONObject op = new JSONObject(((Operation) o).createMap(mind));
                if (sources) {
                    op.put("scripts", o.getScripts());
                }
                list.add(op);
            }
        }

        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    private JSONObject getStatements(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();
        boolean causes = false;
        long predicateId = -1;
        long id = -1;

        if (!parameters.isNull("causes")) {
            causes = parameters.getBoolean("causes");
        }
        if (!parameters.isNull("id")) {
            id = parameters.getLong("id");
        }
        if (!parameters.isNull("predicate")) {
            try {
                predicateId = parameters.getLong("predicate");
            } catch (JSONException e) {
                String pn = parameters.getString("predicate");
                for (IPredicate p : mind.getPredicates()) {
                    if (pn.equals(p.getName(mind))) {
                        predicateId = p.getId();
                    }
                }
            }
        }

        List<JSONObject> list = new ArrayList<>();
        for (IRule r : mind.getRules()) {
            if (!r.isDeleted(mind)
                    && r.isStored()
                    && (id == -1 || id == r.getId())
                    && (predicateId == -1 || r.getPredicate().getId() == predicateId)) {
                JSONObject jr = new JSONObject(((Rule) r).createMap(mind));
                if (causes) {
                    jr.put("causes", recurseCauses(r, mind));
                }
                list.add(jr);
            }
        }

        if (id != -1 && list.isEmpty()) {
            for (IRule r : mind.getSolutions()) {
                if (!r.isDeleted(mind)
                        && r.isStored()
                        && (id == -1 || id == r.getId())
                        && (predicateId == -1 || r.getPredicate().getId() == predicateId)) {
                    JSONObject jr = new JSONObject(((Rule) r).createMap(mind));
                    if (causes) {
                        jr.put("causes", recurseCauses(r, mind));
                    }
                    list.add(jr);
                }
            }
        }

        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    private JSONObject getPredicates(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        boolean statements = false;
        long id = -1;

        if (!parameters.isNull("statements")) {
            statements = parameters.getBoolean("statements");
        }
        if (!parameters.isNull("id")) {
            id = parameters.getLong("id");
        }

        List<JSONObject> list = new ArrayList<>();
        for (IPredicate p : mind.getPredicates()) {
            if (!p.isDeleted(mind) && !((Predicate) p).isSystem((Mind) mind) && (id == -1 || id == p.getId())) {
                JSONObject jp = new JSONObject(((Predicate) p).createMap(mind));
                if (statements) {
                    List<JSONObject> sList = new ArrayList<>();
                    for (IRule r : p.getSolves(mind)) {
                        JSONObject js = new JSONObject(((Rule) r).createMap(mind));
                        sList.add(js);
                    }
                    jp.put("statements", sList);
                }
                list.add(jp);
            }
        }

        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    /**
     * /command?rules=[all|produced][&id=&lt;n&gt;][&level=&lt;n&gt;][&tree=&lt;true|false&gt;][&causes=&lt;true|false&gt;]
     *
     * @param parameters
     * @param user
     * @return
     * @throws Exception
     */
    private JSONObject getRules(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        IMind m = null;
        boolean rules = true;
        boolean tree = false;
        boolean produces = false;
        long id = -1;
        if (!parameters.isNull("level")) {
            long lv = parameters.getLong("level");
            if (lv < 0 || lv > mind.getTransactionLevel()) {
                throw new CommandErrorException("Invalid transaction level " + lv);
            }
            for (m = mind; m != null; m = m.getNext()) {
                if (m.getTransactionLevel() == lv) {
                    break;
                }
            }
        }
        if (!parameters.isNull("tree")) {
            tree = parameters.getBoolean("tree");
        }

        if (parameters.getString("rules").isEmpty()) {
            rules = true;
            produces = false;
        } else if ("all".equalsIgnoreCase(parameters.getString("rules"))) {
            rules = true;
            produces = true;
        } else if ("produced".equalsIgnoreCase(parameters.getString("rules"))) {
            rules = false;
            produces = true;
        } else {
            rules = true;
            produces = false;
        }

        if (!parameters.isNull("id")) {
            id = parameters.getLong("id");
            rules = true;
            produces = true;
        }

        boolean found = false;
        List<JSONObject> list = new ArrayList<>();
        for (IRule r : mind.getRules()) {
            if (!r.isDeleted(m == null ? mind : m)
                    && (r.getId() == id || (id == -1 && (
                    (m == null && produces && r.isGenerated())
                            || (m == null && rules && !r.isGenerated())
                            || (m != null && ((Rule) r).getMindId() == m.getId()))))) {
                found = true;
                JSONObject jr = new JSONObject(((Rule) r).createMap(mind));
                if (tree) {
                    List<List<String>> mt = new ArrayList<>();
                    for (List<Domain> branch : ((Rule) r).getTree()) {
                        List<String> b = new ArrayList<>();
                        mt.add(b);
                        for (Domain d : branch) {
                            b.add(d.toString());
                        }
                    }
                    jr.put("tree", mt);
                }
                list.add(jr);
            }
        }
        result.put("result", "OK");
        result.put("size", list.size());
        result.put("list", list);
        return result;
    }

    private List<JSONObject> recurseCauses(IRule r, IMind mind) throws Exception {
        List<JSONObject> list = new ArrayList<>();
        if (r != null) {
            for (ICause cause : r.getCauses()) {
                JSONObject c = new JSONObject()
                        .put("rule", ((Rule) cause.getRule(mind)).createMap(mind))
                        .put("donor", cause.getDonor(mind) != null ? ((Rule) cause.getDonor(mind)).createMap(mind) : ((Cause) cause).getDonor().createMap(mind))
                        .put("causes", recurseCauses(cause.getDonor(mind), mind));
                list.add(c);
            }
        }
        return list;
    }

    private JSONObject clearWorkspace(IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        mind = mind.clearWorkspace();
        user.setCurrentMind(mind);
        JSONObject result = new JSONObject();
        result.put("result", "OK");
        return result;
    }

    private JSONObject closeDatabase(IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        mind = mind.closeStorage();
        user.setCurrentMind(mind);
        JSONObject result = new JSONObject();
        result.put("result", "OK");
        return result;
    }

    private JSONObject quit(IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        mind.closeStorage();
        user.setCurrentMind(null);
        UserFactory.dropUser(user);
        JSONObject result = new JSONObject();
        result.put("result", "OK");
        Watchdog.log(user, "User left system");
        return result;
    }

    private JSONObject usedDatabase(IUser user) throws Exception {
        JSONObject result = new JSONObject();
        IMind mind = user.getCurrentMind();
        if (mind.isStorageUsed()) {
            result.put("result", "OK");
            result.put("name", mind.getStorageName());
            result.put("rules", mind.getTop().getRules().size());
            result.put("predicates", mind.getTop().getPredicates().size());
            result.put("dictionary", mind.getTerms().size());
            result.put("udf", mind.getTop().getLibrary().size());
            result.put("description", "Database used: " + mind.getStorageName().replace(Enums.FILE_SEPARATOR, ".") +
                    ", Rules: " + mind.getTop().getRules().size() +
                    ", Predicates: " + mind.getTop().getPredicates().size() +
                    ", Dictionary: " + mind.getTerms().size() +
                    ", UDF: " + mind.getTop().getLibrary().size());
        } else {
            result.put("result", "error");
            result.put("description", "No database used");
        }
        return result;
    }

    private JSONObject useDatabase(JSONObject parameters, IUser user) throws Exception {
        JSONObject result = new JSONObject();
        IMind mind = user.getCurrentMind();
        String backup = SourceContextMaterializer.materializeCurrentLevel(mind);

        if (!parameters.getString("use").isEmpty()) {
            String name = parameters.getString("use").replace(".", Enums.FILE_SEPARATOR);
            boolean exists = mind.isStorageExists(name);
            mind = mind.useStorage(name);
            if (mind.isStorageUsed()) {
                if (!exists) {
                    Watchdog.log(user, "New database created: " + name);
                }
                boolean success = true;
                if (!backup.isEmpty()) {
                    IMind m = new Mind(mind);
                    if (m.compile(backup)) {
                        if (!m.isEmptyLevel()) {
                            mind = m;
                            System.out.printf("Transaction level %d (%d)\n", mind.getTransactionLevel(), mind.getId());
                        } else {
                            mind.release(m);
                        }
                    } else {
                        mind = mind.closeStorage();
                        mind.compile(backup);
                        mind.clearLog();
                        mind.release(m);
                        result.put("result", "error");
                        result.put("description", mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord());
                        success = false;
                    }
                }
                if (success) {
                    result = usedDatabase(user);
                }
            } else {
                result.put("result", "error");
                result.put("description", "Error opening database " + parameters.getString("use"));
            }
            user.setCurrentMind(mind);
        } else {
            Collection<String> list = mind.getStoragesList();
            result.put("result", "OK");
            result.put("size", list.size());
            result.put("list", list);
        }
        return result;
    }

    private JSONObject saveSourceFile(JSONObject parameters, IUser user) throws Exception {
        IMind mind = user.getCurrentMind();
        JSONObject result = new JSONObject();

        String fname = parameters.getString("put");
        if (fname != null && !fname.isEmpty()) {
            File f = new File(mind.getUser().getSourceDir() + fname);
            boolean exists = f.exists();
            String source = SourceContextMaterializer.materializeCurrentLevel(mind);
            Files.write(f.toPath(), source.getBytes(StandardCharsets.UTF_8));
            result.put("result", "OK");
            result.put("description", "Source file " + fname + " saved.");
            if (!exists) {
                Watchdog.log(user, "New source file created: " + fname);
            }
        } else {
            result.put("result", "error");
            result.put("description", "You have to select name for file.");
        }
        return result;
    }

    private JSONObject loadSourceFile(JSONObject parameters, IUser user) throws Exception {
        JSONObject result = new JSONObject();
        IMind mind = user.getCurrentMind();
        if (!parameters.getString("get").isEmpty()) {
            File f = new File(user.getSourceDir() + parameters.getString("get"));
            if (f.exists()) {
                if (f.length() > 0) {
                    String source = new String(
                            Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

                    if (mind.isStorageUsed()) {
                        mind = new Mind(mind);
                    }

                    Boolean res = mind.compile(source);
                    String description = mind.getCurrentLogRecord(LogMode.ANALYZER).getRecord();
                    if (res) {
                        description += "<br>" + String.format("File %s loaded", f.getName());
                    }
                    if (mind.isStorageUsed() && mind.isEmptyLevel()) {
                        IMind m = mind.getNext();
                        m.release(mind);
                        mind = m;
                    }
                    if (mind.getTransactionLevel() > 0) {
                        description += "<br>" + String.format("Transaction level %d (%d)", mind.getTransactionLevel(), mind.getId());
                    }
                    ((Mind) mind).setQueryResult(res);

                    user.setCurrentMind(mind);
                    if (mind.getQueryResult() != null && mind.getQueryResult()) {
                        result.put("result", "OK");
                    } else {
                        result.put("result", "error");
                    }
                    result.put("description", description);
                } else {
                    result.put("result", "error");
                    result.put("description", "File is empty " + user.getSourceDir() + parameters.getString("load"));
                }
            } else {
                result.put("result", "error");
                result.put("description", "File not found " + user.getSourceDir() + parameters.getString("load"));
            }
        } else {
            List<String> list = new ArrayList<>();
            File[] dir = new File(mind.getUser().getSourceDir()).listFiles();
            if (dir != null) {
                for (File fl : dir) {
                    if (!fl.isDirectory() && fl.getName().contains(".k")) {
                        list.add(fl.getName());
                    }
                }
            }
            result.put("result", "OK");
            result.put("size", list.size());
            result.put("list", list);
        }
        return result;
    }

}
