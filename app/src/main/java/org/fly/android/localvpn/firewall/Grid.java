package org.fly.android.localvpn.firewall;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.fly.android.localvpn.structs.Jacksonable;
import org.fly.protocol.http.request.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Grid extends Jacksonable {

    public Map<String, Dns> dns = new HashMap<>();
    public Map<String, Http> http = new HashMap<>();

    public void init()
    {
        for (Map.Entry<String, Dns> entry: dns.entrySet()
                ) {
            entry.getValue().pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
        }

        for (Map.Entry<String, Http> entry: http.entrySet()
                ) {
            entry.getValue().pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
        }
    }

    List<String> matchDns(String domain, org.fly.protocol.dns.content.Dns.TYPE type)
    {
        for(Map.Entry<String, Dns> entry: dns.entrySet())
        {
            Matcher matcher = entry.getValue().pattern.matcher(domain);
            if (matcher.find())
            {
                switch (type)
                {
                    case A:
                        return entry.getValue() == null || entry.getValue().A.isEmpty() ? null : entry.getValue().A;
                    case AAAA:
                        return entry.getValue() == null || entry.getValue().AAAA.isEmpty() ? null : entry.getValue().AAAA;
                    case CNAME:
                        return entry.getValue() == null || entry.getValue().CNAME.isEmpty() ? null : entry.getValue().CNAME;
                }
            }
        }

        return null;
    }

    String matchHttp(String url, Method method)
    {
        for(Map.Entry<String, Http> entry: http.entrySet())
        {
            Matcher matcher = entry.getValue().pattern.matcher(url);
            if (matcher.find())
            {
                switch (method)
                {
                    case POST:
                        return entry.getValue() == null || entry.getValue().POST.isEmpty() ? null : entry.getValue().POST;
                    case GET:
                        return entry.getValue() == null || entry.getValue().GET.isEmpty() ? null : entry.getValue().GET;
                    case PUT:
                        return entry.getValue() == null || entry.getValue().PUT.isEmpty() ? null : entry.getValue().PUT;
                    case DELETE:
                        return entry.getValue() == null || entry.getValue().DELETE.isEmpty() ? null : entry.getValue().DELETE;
                }
            }
        }
        return null;
    }

    static class Dns {
        @JsonIgnore
        Pattern pattern;
        public List<String> A = new ArrayList<>();
        public List<String> AAAA = new ArrayList<>();
        public List<String> CNAME = new ArrayList<>();
    }

    static class Http {
        @JsonIgnore
        Pattern pattern;
        public String POST = null;
        public String GET = null;
        public String PUT = null;
        public String DELETE = null;
    }

}