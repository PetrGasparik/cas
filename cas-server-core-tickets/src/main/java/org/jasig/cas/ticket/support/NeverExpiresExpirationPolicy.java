package org.jasig.cas.ticket.support;

import org.jasig.cas.ticket.TicketState;
import org.springframework.stereotype.Component;

/**
 * NeverExpiresExpirationPolicy always answers false when asked if a Ticket is
 * expired. Use this policy when you want a Ticket to live forever, or at least
 * as long as the particular CAS Universe exists.
 *
 * @author Scott Battaglia
 * @since 3.0.0
 */
@Component("neverExpiresExpirationPolicy")
public final class NeverExpiresExpirationPolicy extends AbstractCasExpirationPolicy {

    /** Serializable Unique ID. */
    private static final long serialVersionUID = 3833747698242303540L;

    /**
     * Instantiates a new Never expires expiration policy.
     */
    public NeverExpiresExpirationPolicy() {}

    @Override
    public boolean isExpired(final TicketState ticketState) {
        return false;
    }

    @Override
    public Long getTimeToLive() {
        return new Long(Integer.MAX_VALUE);
    }

    @Override
    public Long getTimeToIdle() {
        return new Long(Integer.MAX_VALUE);
    }
}
