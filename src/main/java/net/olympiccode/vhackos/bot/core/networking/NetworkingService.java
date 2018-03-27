package net.olympiccode.vhackos.bot.core.networking;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.sentry.Sentry;
import net.olympiccode.vhackos.api.entities.AppType;
import net.olympiccode.vhackos.api.entities.BruteForceState;
import net.olympiccode.vhackos.api.entities.impl.BruteForceImpl;
import net.olympiccode.vhackos.api.exceptions.ExploitFailedException;
import net.olympiccode.vhackos.api.network.BruteForce;
import net.olympiccode.vhackos.api.network.ExploitedTarget;
import net.olympiccode.vhackos.api.network.NetworkManager;
import net.olympiccode.vhackos.bot.core.BotService;
import net.olympiccode.vhackos.bot.core.vHackOSBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.nio.channels.NetworkChannel;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.olympiccode.vhackos.api.entities.impl.vHackOSAPIImpl;
import net.olympiccode.vhackos.api.vHackOSAPI;
import net.olympiccode.vhackos.api.vHackOSAPIBuilder;
import net.olympiccode.vhackos.api.vHackOSInfo;


public class NetworkingService implements BotService {
    public static ScheduledExecutorService networkingService;
    Logger LOG = LoggerFactory.getLogger("NetworkingService");

    public NetworkingService() {
        LOG.info("Creating NetworkingService...");
        networkingService = Executors.newScheduledThreadPool(1, new NetworkingServiceFactory());
    }

    public class NetworkingServiceFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            return new Thread(r,   "vHackOSBot-NetworkingService");
        }
    }


    @Override
    public ScheduledExecutorService getService() {
        return networkingService;
    }

    public void setup() {
        LOG.info("Setting up NetworkingSerice...");
        networkingService.scheduleAtFixedRate(() -> runService(), 0, 60000, TimeUnit.MILLISECONDS);
    }

    Cache<String, String> cache = CacheBuilder.newBuilder()
            .maximumSize(500)
       .expireAfterWrite(60*5+30, TimeUnit.SECONDS).build();

    public void runService() {
        try {
            ((ArrayList<BruteForce>)((ArrayList)vHackOSBot.api.getTaskManager().getActiveBrutes()).clone()).forEach(bruteForce -> {
                if (cache.asMap().containsKey(bruteForce.getIp())) return;
                if (bruteForce.getState() == BruteForceState.SUCCESS) {
if (vHackOSBot.api.getStats().getMoney() < 1000000) { // 1M
LOG.info("FILLUP mybank: " + vHackOSBot.api.getStats().getMoney()/1000000.0 + "M");
} else {
//LOG.info("BANKFULL: bruteip: " + bruteForce.getIp() + " - mybank: " + vHackOSBot.api.getStats().getMoney()/1000000.0 + "M");
//LOG.info("BANKFULL: bruteip: " + bruteForce.getIp());
return;
}
                    cache.put(bruteForce.getIp(), "");
                    ExploitedTarget etarget = bruteForce.exploit();
                    ExploitedTarget.Banking banking = etarget.getBanking();

                    if (banking.isBruteForced()) {
                        long av = banking.getAvaliableMoney();
//MJR`
//                        if (av > 0 && banking.withdraw(NetworkingConfigValues.withdrawPorcentage)) {
			// 1M
                        if (av > 0 && av > 1000000 && eval(etarget) == false) {
//&& vHackOSBot.api.getStats().getMoney() < 5000000) { 
				banking.withdraw(NetworkingConfigValues.withdrawPorcentage);
//                            LOG.info("WITHDRAW: " + av/1000000. + "M of " + banking.getTotal() + " from " + etarget.getIp() + ".");
                            LOG.info("WITHDRAW: " + av/1000000. + "M from: " + etarget.getIp());
                        } else {
                            LOG.error("Failed to withdraw from " + etarget.getIp() + ".");
			    LOG.error("av: " + av/1000000. + "M  - eval: " + eval(etarget) + " - mybank: " + vHackOSBot.api.getStats().getMoney()/1000000 + "M");
                        }
//LOG.info("Bank info: " + av + " - " + banking.getTotal() + " - " + banking.getSavings() );
                        if (eval(etarget)) {
//MJR
//LOG.info("Would remove");
//                            LOG.info("Removing bruteforce from " + etarget.getIp() + ".");
//                            LOG.info("Removing bruteforce from " + etarget.getIp() + ". - " + av + " - " + etarget.getSavings() + " - " + etarget.maxSavings() + " - " + etarget.getTotal());
                            //bruteForce.remove();
                        }

                    } else {
                        if (banking.startBruteForce()) {
                            LOG.info("Started bruteforce at " + etarget.getIp());
                        } else {
                            LOG.error("Failed to start bruteforce at " + etarget.getIp());
                        }
                    }
                    etarget.setSystemLog(NetworkingConfigValues.logMessage.replaceAll("%username%", vHackOSBot.api.getStats().getUsername()));
                } else if (bruteForce.getState() == BruteForceState.FAILED) {
                    switch (NetworkingConfigValues.onFail) {
                        case "retry":
                            LOG.info("Retrying bruteforce at " + bruteForce.getIp() + " has it failed.");
                            bruteForce.retry();
                        case "remove":
                            LOG.info("Removing bruteforce from " + bruteForce.getIp() + " has it failed.");
                            bruteForce.remove();
                    }
		}
	    });
//	});
            if (vHackOSBot.api.getStats().getExploits() > 0) {
               int success = 0;
               int tries = 6 * 3;
                LOG.info("Starting exploits...");
               while (success < 6 && tries > 0) {
                   success += scan();
                   tries--;
               }
               LOG.info("Finished exploits, exploited " + success + " targets in " + tries + " tries.");
            }
        } catch (Exception e) {
            Sentry.capture(e);
            e.printStackTrace();
            networkingService.shutdownNow();
            LOG.warn("The networking service has been shutdown due to an error.");
        }
    }

    public int scan() {
        final int[] success = {0};
        vHackOSBot.api.getNetworkManager().getTargets().forEach(target -> {
            if (vHackOSBot.api.getStats().getExploits() <= 0) return;
// MJR	
//            if (target.getFirewall() < vHackOSBot.api.getAppManager().getApp(AppType.SDK).getLevel() && !target.isOpen()) {
LOG.info("fw level: " + target.getFirewall());
            if (target.getFirewall() < vHackOSBot.api.getAppManager().getApp(AppType.SDK).getLevel() && target.getFirewall() > 100 && !target.isOpen()) {
                success[0]++;
                //LOG.info("Exploiting " + target.getIp() + "...");
                try {
                    ExploitedTarget etarget = target.exploit();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //LOG.info("Starting bruteforce on " + target.getIp() + "...");
                    if (etarget.getBanking().startBruteForce()) {
                        LOG.info("Started bruteforce on " + target.getIp() + ".");
                    } else {
                        LOG.error("Failed to start bruteforce on " + target.getIp() + ".");
                    }
                    etarget.setSystemLog(NetworkingConfigValues.logMessage.replaceAll("%username%", vHackOSBot.api.getStats().getUsername()));
                } catch (ExploitFailedException e) {
                    LOG.warn("Failed to exploit " + target.getIp() + ": " + e.getMessage());
                }
            }
        });
        return success[0];
    }

    public boolean eval(ExploitedTarget target) {
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("JavaScript");
        String foo = NetworkingConfigValues.bruteForceRemove;
        foo = foo.replaceAll("%savings%", String.valueOf(target.getBanking().getSavings()));
        foo = foo.replaceAll("%maxsavings%", String.valueOf(target.getBanking().getMaxSavings()));
        foo = foo.replaceAll("%total%", String.valueOf(target.getBanking().getTotal()));
        try {
            return (boolean) engine.eval(foo);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        return false;
    }
}
