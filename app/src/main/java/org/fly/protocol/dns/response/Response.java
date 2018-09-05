package org.fly.protocol.dns.response;

import org.fly.protocol.dns.content.Dns;

public class Response extends Dns {

    public Response() {
    }

    public Response(int id, String domain, String value, TYPE type, int ttl) {
        header.setId(id);
        header.setQr(1);
        header.setOpcode(OPCODE.QUERY);
        header.setRd(1);
        header.setRcode(RCODE.OK);
        header.setQdCount(1);
        header.setAnCount(1);

        Query question = new Query(domain, type);
        questions.add(question);

        Record answer = new Record(domain, type, value, ttl);
        answers.add(answer);
    }

    public static Response create(int id, String domain, String value, Dns.TYPE type, int ttl)
    {
        return new Response(id, domain, value, type, ttl);
    }

    public static Response create(int id, String domain, String value)
    {
        return create(id, domain, value, TYPE.A, 600);
    }

    public static Response create(int id, String domain, String value, int ttl)
    {
        return create(id, domain, value, TYPE.A, ttl);
    }

}
