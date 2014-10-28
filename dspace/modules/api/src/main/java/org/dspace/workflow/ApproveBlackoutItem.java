/*
 */
package org.dspace.workflow;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import javax.mail.MessagingException;
import org.apache.log4j.Logger;
import org.datadryad.api.DryadDataPackage;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.workflow.actions.WorkflowActionConfig;

/**
 * Class to approve an item in Publication Blackout
 * @author Dan Leehr <dan.leehr@nescent.org>
 */
public class ApproveBlackoutItem {
    private static final Logger log = Logger.getLogger(ApproveBlackoutItem.class);

    private static Boolean isClaimed(Context c, WorkflowItem wfi) throws SQLException {
        List<ClaimedTask> claimedTasks = ClaimedTask.findByWorkflowId(c, wfi.getID());
        // If there are claimed tasks for this workflow item, it is claimed
        return !claimedTasks.isEmpty();
    }

    private static EPerson getSystemEPerson(Context c) {
        return null;
    }

    // Make it testable!
    public static void approveBlackoutItemDOI(Context c, String doi) {
        // look up the workflow item by DOI and approve it from blackout
    }

    private static Boolean isBlackoutApproveTask(ClaimedTask t) {
        return t.getActionID().equals("afterPublicationAction");
    }

    private static Boolean isBlackoutApproveStep(PoolTask p) throws SQLException {
        return (p.getStepID().equals("pendingPublicationStep") ||
                p.getStepID().equals("pendingPublicationReAuthorizationPaymentStep"));
    }

    private static void deleteClaimedTask(Context c, WorkflowItem wfi, ClaimedTask claimedTask) throws SQLException, ApproveBlackoutItemException {
        try {
            WorkflowManager.deleteClaimedTask(c, wfi, claimedTask);
        } catch (AuthorizeException ex) {
            throw new ApproveBlackoutItemException("Unable to delete claimed task", ex);
        }
    }

    // TODO: Implement a caller
    // Should create the context and close it at the end
    private static Boolean approveBlackoutItem(Context c, WorkflowItem wfi) throws SQLException, ApproveBlackoutItemException, ItemIsNotInBlackoutException {
        if(wfi == null) {
            throw new ApproveBlackoutItemException("Cannot approve null item");
        } else if(c == null) {
            throw new ApproveBlackoutItemException("Cannot approve item with null context");
        }

        DryadDataPackage dataPackage = new DryadDataPackage(wfi.getItem());
        if(dataPackage == null) {
            throw new ApproveBlackoutItemException("Unable to find data package for item " + wfi.getItem());
        }

        // Item must not already be claimed
        if(isClaimed(c, wfi)) {
            throw new ApproveBlackoutItemException("Cannot approve item that is already claimed by a user");
        }

        // Must have a task in the pool for this user
        EPerson eperson = getSystemEPerson(c);
        if(eperson == null) {
            throw new ApproveBlackoutItemException("Cannot get system eperson to approve blackout item");
        }
        PoolTask poolTask = PoolTask.findByWorkflowIdAndEPerson(c, wfi.getID(), eperson.getID());
        if(poolTask == null) {
            // Task
            throw new ApproveBlackoutItemException("Cannot find task to claim for wfi: " + wfi.getID() + " ePersonID:" + eperson.getID());
        }

        // Before claiming, make sure the task is a blackout approval
        // We don't handle anything else

        if(!isBlackoutApproveStep(poolTask)) {
            // the step to claim is not blackout, abort
            throw new ItemIsNotInBlackoutException("Task for wfi: " + wfi.getID() + " ePersonID: " + eperson.getID() + " is not a blackout task - item is not in blackout, returning");
        }

        Workflow workflow = null;
        Step step = null;
        WorkflowActionConfig action = null;

        try {
            workflow = WorkflowFactory.getWorkflow(wfi.getCollection());
            step = workflow.getStep(poolTask.getStepID());
            action = step.getActionConfig(poolTask.getActionID());
            // This method does not return the created task, so it must be fetched separately
            WorkflowManager.createOwnedTask(c, wfi, step, action, eperson);
        } catch (IOException ex) {
            throw new ApproveBlackoutItemException("IOException getting workflow", ex);
        } catch (WorkflowConfigurationException ex) {
            throw new ApproveBlackoutItemException("WorkflowConfigurationException getting workflow", ex);
        } catch (AuthorizeException ex) {
            throw new ApproveBlackoutItemException("AuthorizeException creating task", ex);
        }

        // Fetch the just-claimed task - wfi and eperson should match
        ClaimedTask claimedTask = ClaimedTask.findByWorkflowIdAndEPerson(c, wfi.getID(), eperson.getID());

        if(claimedTask == null) {
            // This is truly exceptional - we successfully created a task but couldn't find it
            throw new ApproveBlackoutItemException("Unable to find just-claimed task for wfi: " + wfi.getID() + " ePersonID:" + eperson.getID());
        }

        if(!isBlackoutApproveTask(claimedTask)) {
            // We claimed a task but it's not a blackout approve task
            deleteClaimedTask(c, wfi, claimedTask);
            throw new ApproveBlackoutItemException("Just-claimed task is NOT a blackout task, serious internal error");
        }

        // Verify date is in the past
        Date now = new Date();
        Date blackoutUntilDate = dataPackage.getBlackoutUntilDate();
        if(blackoutUntilDate == null) {
            log.error("Attempted to lift blackout on item: " + wfi.getItem().getID() + " but no blackoutUntilDate present");
            // Too early, delete the task
            deleteClaimedTask(c, wfi, claimedTask);
            return Boolean.FALSE;
        }

        if(now.before(blackoutUntilDate)) {
            // current date is before the blackout until date
            log.error("Attempted to lift blackout early on item " + wfi.getItem().getID() +
                    ". Current date: " + now + " blackoutUntilDate: " + blackoutUntilDate);
            deleteClaimedTask(c, wfi, claimedTask);
            return Boolean.FALSE;
        }

        // At this point, correct task is claimed and the blackout date is in the past.
        // Just need to execute it

        try {
            // WorkflowManager.doState: "Executes an action and returns the next."
            WorkflowManager.doState(c, eperson, null, claimedTask.getWorkflowItemID(), workflow, action);
        } catch (IOException ex) {
            throw new ApproveBlackoutItemException("IOException approving out of blackout", ex);
        } catch (WorkflowConfigurationException ex) {
            throw new ApproveBlackoutItemException("WorkflowConfigurationException approving out of blackout", ex);
        } catch (AuthorizeException ex) {
            throw new ApproveBlackoutItemException("AuthorizeException approving out of blackout", ex);
        } catch (MessagingException ex) {
            throw new ApproveBlackoutItemException("MessagingException approving out of blackout", ex);
        } catch (WorkflowException ex) {
            throw new ApproveBlackoutItemException("WorkflowException approving out of blackout", ex);
        }

        // TODO: Implement the system user
        // TODO: task to find eligible items in the workflow and approve them
        return Boolean.TRUE;
    }
}
