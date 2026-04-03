package com.masterofpuppets.voxpop;

import com.masterofpuppets.voxpop.network.signaling.SignalingServer;
import com.masterofpuppets.voxpop.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModeratorApplication {

    private static final Logger logger = LoggerFactory.getLogger(ModeratorApplication.class);
    private static final int PORT = 8887;
    private static final String SERVICE_TYPE = "_voxpop._tcp.local.";
    private static final String SERVICE_NAME = "VoxPop Moderator";

    private static final Map<String, JmDNS> jmdnsInstances = new ConcurrentHashMap<>();
    private static final AtomicBoolean isNetworkLocked = new AtomicBoolean(false);

    public static void main(String[] args) {
        SessionManager sessionManager = new SessionManager();

        SignalingServer signalingServer = new SignalingServer(PORT, sessionManager, localIp -> {
            if (isNetworkLocked.compareAndSet(false, true)) {
                logger.info("Network locked to IP: {}", localIp);
                lockNetworkToIp(localIp);
            }
        });
        signalingServer.start();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        try {
                            JmDNS jmdns = JmDNS.create(addr);
                            ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE, SERVICE_NAME, PORT, "VoxPop Signaling Server");
                            jmdns.registerService(serviceInfo);
                            jmdnsInstances.put(addr.getHostAddress(), jmdns);

                            logger.info("mDNS announced on interface: {} ({})", ni.getDisplayName(), addr.getHostAddress());
                        } catch (Exception e) {
                            logger.error("Failed to announce on {}", addr.getHostAddress(), e);
                        }
                    }
                }
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down Moderator Application...");
                for (JmDNS jmdns : jmdnsInstances.values()) {
                    jmdns.unregisterAllServices();
                    try {
                        jmdns.close();
                    } catch (Exception e) {
                        logger.error("Error closing mDNS instance", e);
                    }
                }
                try {
                    signalingServer.stop();
                } catch (InterruptedException e) {
                    logger.error("Error stopping server", e);
                }
            }));

        } catch (Exception e) {
            logger.error("Error initializing network interfaces", e);
        }
    }

    private static void lockNetworkToIp(String activeIp) {
        jmdnsInstances.forEach((ip, jmdns) -> {
            if (!ip.equals(activeIp)) {
                logger.info("Closing mDNS on unused IP: {}", ip);
                jmdns.unregisterAllServices();
                try {
                    jmdns.close();
                } catch (Exception e) {
                    logger.error("Error closing JmDNS on {}", ip, e);
                }
            }
        });
        jmdnsInstances.keySet().removeIf(ip -> !ip.equals(activeIp));
        logger.info("Network successfully locked. Only listening on {}", activeIp);
    }
}