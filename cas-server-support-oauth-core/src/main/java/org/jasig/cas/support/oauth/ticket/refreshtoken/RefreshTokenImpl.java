package org.jasig.cas.support.oauth.ticket.refreshtoken;

import org.jasig.cas.authentication.Authentication;
import org.jasig.cas.authentication.principal.Service;
import org.jasig.cas.support.oauth.ticket.code.OAuthCodeImpl;
import org.jasig.cas.ticket.ExpirationPolicy;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * An OAuth refresh token implementation.
 *
 * @author Jerome Leleu
 * @since 5.0.0
 */
@Entity
@DiscriminatorValue(RefreshToken.PREFIX)
public class RefreshTokenImpl extends OAuthCodeImpl implements RefreshToken {

    private static final long serialVersionUID = -3544459978950667758L;

    /**
     * Instantiates a new OAuth refresh token.
     */
    public RefreshTokenImpl() {
        // exists for JPA purposes
    }

    /**
     * Constructs a new refresh token with unique id for a service and authentication.
     *
     * @param id the unique identifier for the ticket.
     * @param service the service this ticket is for.
     * @param authentication the authentication.
     * @param expirationPolicy the expiration policy.
     * @throws IllegalArgumentException if the service or authentication are null.
     */
    public RefreshTokenImpl(final String id, @NotNull final Service service, @NotNull final Authentication authentication,
                            final ExpirationPolicy expirationPolicy) {
        super(id, service, authentication, expirationPolicy);
    }
}
