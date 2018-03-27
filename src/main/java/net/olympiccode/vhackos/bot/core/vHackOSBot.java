package net.olympiccode.vhackos.bot.core;

import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.UserBuilder;
import net.olympiccode.vhackos.api.entities.impl.vHackOSAPIImpl;
import net.olympiccode.vhackos.api.vHackOSAPI;
import net.olympiccode.vhackos.api.vHackOSAPIBuilder;
import net.olympiccode.vhackos.api.vHackOSInfo;
import net.olympiccode.vhackos.bot.core.config.AdvancedConfigFile;
import net.olympiccode.vhackos.bot.core.config.AdvancedConfigValues;
import net.olympiccode.vhackos.bot.core.config.ConfigFile;
import net.olympiccode.vhackos.bot.core.config.ConfigValues;
import net.olympiccode.vhackos.bot.core.misc.MiscConfigValues;
import net.olympiccode.vhackos.bot.core.misc.MiscService;
import net.olympiccode.vhackos.bot.core.networking.NetworkingConfigValues;
import net.olympiccode.vhackos.bot.core.networking.NetworkingService;
import net.olympiccode.vhackos.bot.core.updating.UpdateConfigValues;
import net.olympiccode.vhackos.bot.core.updating.UpdateService;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class vHackOSBot {

    public static vHackOSAPI api;
    static Logger LOG = LoggerFactory.getLogger("vHackOSBot");
    ConfigFile config = new ConfigFile();
    AdvancedConfigFile advConfig = new AdvancedConfigFile();
    BotService updateService = new UpdateService();
    BotService miscService = new MiscService();
    BotService networkingService = new NetworkingService();

    public static void main(String[] args) {
        try {
            new vHackOSBot().run();
        } catch (LoginException e) {
            LOG.error("vHack returned invalid username/password.");
        } catch (InterruptedException e) {
            LOG.error("There was a problem initializing the vHackOSBot.");
        } catch (RuntimeException e) {
            if (e.getMessage().contains("vhack account has been banned")) {
                LOG.error("Your vhack account has been banned.");
                System.exit(0);
            } else {
                Sentry.capture(e);
                e.printStackTrace();
            }
        } catch (Exception e) {
            Sentry.capture(e);
            e.printStackTrace();
        }
    }

    public void run() throws LoginException, InterruptedException {
        Sentry.init("https://36b5e13fe253466f8b98b5adacb2aa32:cf886218c21b4ba7ad4692f303020f7a@sentry.io/303008");
        Sentry.getContext().addExtra("version", vHackOSInfo.API_PREFIX);
        Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("Starting...").build()
        );
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            config.save();
            advConfig.save();
        }));
        Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("Configs").build()
        );
        try {
            advConfig.setupConfig();
            config.setupConfig();
        } catch (Exception e) {
            Sentry.capture(e);
            e.printStackTrace();
        }
        Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("Configs done").build()
        );
//        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        //  root.setLevel(Level.valueOf(AdvancedConfigValues.logLevel));
        Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("API setup").build()
        );

        if (ConfigValues.username.equals("***") || ConfigValues.password.equals("***")) {
            LOG.error("Please set your login data in the config file");
            System.exit(0);
        }
        if (!AdvancedConfigValues.token.equals("---") && !AdvancedConfigValues.token.equals("---")) {
            api = new vHackOSAPIBuilder().setUsername(ConfigValues.username).setPassword(ConfigValues.password).setPreLogin(AdvancedConfigValues.token, AdvancedConfigValues.uid).buildBlocking();
        } else {
            api = new vHackOSAPIBuilder().setUsername(ConfigValues.username).setPassword(ConfigValues.password).buildBlocking();
        }
          advConfig.getConfigJson().addProperty("login.accesstoken", ((vHackOSAPIImpl) api).getAccessToken());
        advConfig.getConfigJson().addProperty("login.uid", ((vHackOSAPIImpl) api).getUid());
        advConfig.save();
        Sentry.getContext().setUser(
                new UserBuilder().setUsername(ConfigValues.username).build()
        );
        Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("Service setup").build()
        );
        try {
            startTime = System.currentTimeMillis();
            if (UpdateConfigValues.enabled) updateService.setup();
            if (MiscConfigValues.enabled) miscService.setup();
            if (NetworkingConfigValues.enabled) networkingService.setup();

        } catch (Exception e) {
            Sentry.capture(e);
            e.printStackTrace();
        }
        Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("Done").build()
        );
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line = "";
        while (true) {
            try {
                String SEPARATOR = "     ";
                line = in.readLine();
                if (line == null) continue;
                String[] args = line.split(" ");
                switch (args[0]) {
                    case "help":
                        System.out.println("stats - List all basic stats\n" + "tasks - Lists all active tasks\n" + "brutes - Lists all active bruteforces" + "\nservices - Check the services status\napps - Check app stats\nquit - Exit the bot\nleaders - Check leaderboards");
                        break;
                    case "stats":
			// MJR
                        System.out.println("Username: " + api.getStats().getUsername() + SEPARATOR + "Money: " + api.getStats().getMoney()/1000000.0 + "M" + SEPARATOR + "Netcoins: " + api.getStats().getNetcoins() +
                                "\n" + "Exploits: " + api.getStats().getExploits() + SEPARATOR + "IP: " + api.getStats().getIpAddress() + SEPARATOR + " Running for: " + getRunningTime() + "\n" +
                                "Level: " + api.getStats().getLevel() + getProgressBar());
                        break;
                    case "tasks":
                        System.out.println("-------------------\n" + "Boosters: " + api.getTaskManager().getBoosters() + "\n-------------------\n" + api.getTaskManager().getActiveTasks().stream().map(task -> task.getType() + ": " + task.getLevel() + " " + ((task.getEndTimestamp() - System.currentTimeMillis()) / 1000) + "sec left.").collect(Collectors.joining("\n")) + "\n-------------------");
                        break;
                    case "brutes":
                        System.out.println("-------------------\n" + api.getTaskManager().getActiveBrutes().stream().map(bruteForce -> bruteForce.getUsername() + ": " + bruteForce.getState()).collect(Collectors.joining("\n")) + "\n-------------------");
                        break;
                    case "services":
                        System.out.println("NetworkingService: " + getStatus(networkingService.getService()) + "\n" +
                                "UpdateService: " + getStatus(updateService.getService()) + "\n" +
                                "MiscService: " + getStatus(miscService.getService()));
                        break;
                    case "apps":
                        System.out.println("-------------------\n" + api.getAppManager().getApps().stream().map(app -> app.getType().getName() + ": " + (app.isInstalled() ? app.getLevel() : "Not installed")).collect(Collectors.joining("\n")) + "\n-------------------");
                        break;
                    case "leader":
                    case "leaders":
                    case "leaderboards":
                        System.out.println("-------------------\n" + "Current tournament pos: " + api.getLeaderboards().getTournamentRank() + "\nTournament ends in: "  + getTimeLeft() + "\nCurrent global pos: " + api.getLeaderboards().getRank() + "\n-------------------");
                        break;
                    case "quit":
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Unkown command, use \"help\" to list all commands.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getStatus(ScheduledExecutorService service) {
        if (service.isShutdown() || service.isTerminated()) return "Stopped.";
        return "Running.";
    }

    private String getTimeLeft() {
        long time = api.getLeaderboards().getTournamentEndTimestamp();
        long millis = time - System.currentTimeMillis();
        return String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }
    private long startTime = 0;
    private String getRunningTime() {
        long millis = System.currentTimeMillis() - startTime;
        return String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    private String getProgressBar() {
        long barnum = (Math.round(api.getStats().getLevelPorcentage() / 10)) - 1;
        StringBuilder builder = new StringBuilder();
        builder.append("    [");
        for (int i = 0; i < 10; i++) {
            if (i <= barnum) {
                builder.append("||");
            } else {
                builder.append("  ");
            }
        }
        builder.append("] " + api.getStats().getLevelPorcentage() + "%");
        return builder.toString();
    }

    public void checkForUpdates() {
        //
        //
        Request request = (new Request.Builder()).url("https://api.github.com/repos/OlympicCode/vHackOSBot-Java/releases/latest").addHeader("user-agent", "Dalvik/1.6.0 (Linux; U; Android 4.4.4; SM-N935F Build/KTU84P)").addHeader("Accept-Encoding", "gzip").build();
        try {
            Response r = ((vHackOSAPIImpl) api).getRequester().getHttpClient().newCall(request).execute();
            if (r.isSuccessful()) {
                JSONObject json = new JSONObject(r.body());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
