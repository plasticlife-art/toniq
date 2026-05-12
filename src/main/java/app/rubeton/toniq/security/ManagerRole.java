package app.rubeton.toniq.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.UiMinimalPolicies;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(name = "Manager", code = ManagerRole.CODE)
public interface ManagerRole extends UiMinimalPolicies {

    String CODE = "manager";

    @ViewPolicy(viewIds = {"MainView", "LoginView", "Event.list", "Event.detail"})
    @MenuPolicy(menuIds = {"application", "Event.list"})
    @SpecificPolicy(resources = "ui.loginToUi")
    void uiAccess();

    @EntityPolicy(entityName = "Event", actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityName = "Event", attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void event();

    @EntityPolicy(entityName = "Organiser", actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityName = "Organiser", attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void organiser();

    @EntityPolicy(entityName = "EventTicketTier", actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityName = "EventTicketTier", attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void eventTicketTier();

    @EntityPolicy(entityName = "EventSyncLog", actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityName = "EventSyncLog", attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void eventSyncLog();

    @EntityPolicy(entityName = "EventSyncState", actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityName = "EventSyncState", attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void eventSyncState();

    @EntityPolicy(entityName = "EventStatusOverride", actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityName = "EventStatusOverride", attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void eventStatusOverride();

    @EntityPolicy(
            entityName = "EventPublicationSettings",
            actions = {
                    EntityPolicyAction.READ,
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.UPDATE
            }
    )
    @EntityAttributePolicy(
            entityName = "EventPublicationSettings",
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void eventPublicationSettings();
}