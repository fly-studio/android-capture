package org.fly.protocol.dns.request;

import org.fly.protocol.dns.content.Dns;

public class Request extends Dns {

    public Request() {
    }

    public Request(String domain, TYPE type) {
        header.setId(generateId());
        header.setRd(1);

        Query question = new Query(domain, type);
        addQuestion(question);
    }

    public static Request create(String domain, TYPE type)
    {
        return new Request(domain, type);
    }

    public static Request create(String domain)
    {
        return new Request(domain, TYPE.A);
    }
}
