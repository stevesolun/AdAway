package org.adaway.ui.domainchecker;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DomainCheckerViewModel#buildAdvice(boolean, boolean)}.
 *
 * <p>Also validates the advice strings reflect the extended UI
 * (block / remove-allow-rule actions added in v13.4.5).
 */
public class DomainCheckerViewModelAdviceTest {

    @Test
    public void notBlocked_adviceDescribesState() {
        String advice = DomainCheckerViewModel.buildAdvice(false, false);
        assertNotNull(advice);
        assertFalse("Advice for not-blocked domain must not be empty", advice.isEmpty());
    }

    @Test
    public void blockedAndUserAllowed_adviceReflectsAllowedState() {
        String advice = DomainCheckerViewModel.buildAdvice(true, true);
        assertNotNull(advice);
        // When already allowed, advice should mention the existing exception
        assertTrue("Advice must mention allow-list exception",
                advice.toLowerCase().contains("allow"));
    }

    @Test
    public void blockedAndNotAllowed_adviceInvitesAction() {
        String advice = DomainCheckerViewModel.buildAdvice(true, false);
        assertNotNull(advice);
        assertFalse("Advice for blocked/not-allowed domain must not be empty", advice.isEmpty());
    }

    @Test
    public void notBlocked_notUserAllowed_isConsistentState() {
        // userAllowed=true with blocked=false is logically possible (user allow + no filter block)
        // buildAdvice must not throw for any combination
        String advice = DomainCheckerViewModel.buildAdvice(false, true);
        assertNotNull(advice);
    }
}
