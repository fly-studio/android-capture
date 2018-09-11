package org.fly.android.localvpn.store;

import android.util.Log;

import org.fly.android.localvpn.Packet;
import org.fly.android.localvpn.firewall.Firewall;
import org.fly.core.cache.LRUCache;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.util.Iterator;
import java.util.Map;

/**
 * User Datagram Block
 */
public class UDB extends Block
{
    private static final String TAG = UDB.class.getSimpleName();

    public DatagramChannel channel;

    private static LRUCache<String, UDB> udpCache =
            new LRUCache<>(MAX_CACHE_SIZE, new LRUCache.CleanupCallback<String, UDB>()
            {
                @Override
                public void cleanup(Map.Entry<String, UDB> eldest)
                {
                    eldest.getValue().closeChannel();
                }
            });

    public static UDB getUDB(String ipAndPort)
    {
        synchronized (udpCache)
        {
            return udpCache.get(ipAndPort);
        }
    }

    public static void putUDB(String ipAndPort, UDB udb)
    {
        synchronized (udpCache)
        {
            udpCache.put(ipAndPort, udb);
        }
    }

    public UDB(String ipAndPort, DatagramChannel channel, Packet referencePacket) {
        this.ipAndPort = ipAndPort;
        this.channel = channel;
        this.referencePacket = referencePacket;
        firewall = new Firewall(Packet.IP4Header.TransportProtocol.UDP, this);
    }

    public static void closeUDB(UDB udb)
    {
        Log.d(TAG, "Close Connection:" + udb.getIpAndPort());

        udb.closeChannel();

        synchronized (udpCache)
        {
            udpCache.remove(udb.ipAndPort);
        }
    }

    public static void closeAll()
    {
        synchronized (udpCache)
        {
            Iterator<Map.Entry<String, UDB>> it = udpCache.entrySet().iterator();
            while (it.hasNext())
            {
                it.next().getValue().closeChannel();
                it.remove();
            }
        }
    }

    protected void closeChannel()
    {
        try
        {
            channel.close();
        }
        catch (IOException e)
        {
            // Ignore
        }
    }
}
