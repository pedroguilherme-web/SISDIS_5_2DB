
package aisafe.p2p;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Service
public class PeerTokenService {

    private final SecretKey key;
    private final long expirationMs;

    public PeerTokenService(
            @Value("${security.p2p.jwt.secret}") String secret,
            @Value("${security.p2p.jwt.expiration-ms}") long expirationMs) {

        this.key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
        this.expirationMs = expirationMs;
    }

    public String generateToken(PeerIdentity identity) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(identity.getPeerId())
                .claim("roles", List.of("PEER"))
                .claim("token_type", "P2P")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return "P2P".equals(claims.get("token_type", String.class))
                    && claims.getSubject() != null
                    && !claims.getSubject().isBlank();

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }


    public String extractPeerId(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

}
