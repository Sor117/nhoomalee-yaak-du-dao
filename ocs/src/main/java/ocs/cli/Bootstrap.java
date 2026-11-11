package ocs.cli;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ocs.catalog.SimulatedStarCatalog;
import ocs.domain.ObservingProgramRepository;
import ocs.domain.SciencePlanRepository;
import ocs.domain.StarCatalog;
import ocs.persistence.FileObservingProgramRepository;
import ocs.persistence.FileSciencePlanRepository;
import ocs.service.CreateObservingProgramService;
import ocs.service.CreateSciencePlanService;
import ocs.service.SubmitSciencePlanService;
import ocs.service.ValidateSciencePlanService;
import ocs.validation.CreatorNotBlankRule;
import ocs.validation.FundingNotBlankRule;
import ocs.validation.ObjectiveNotBlankRule;
import ocs.validation.ObservationMonthRule;
import ocs.validation.PlanIdPositiveRule;
import ocs.validation.PlanNameNotBlankRule;
import ocs.validation.PlanValidator;
import ocs.validation.QuadrantTelescopeConsistencyRule;

/**
 * Composition root: the only class that wires implementations to ports.
 */
public final class Bootstrap {
    private static final String USAGE = "Usage: Bootstrap <command> [--option value ...] | commands: create-plan, mark-tested, submit-plan, validate-plan, create-program";

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        AbstractCliApp app = createApplications().get(args[0]);
        if (app == null) {
            printUsage();
            return;
        }
        app.run(Arrays.copyOfRange(args, 1, args.length));
    }

    private static void printUsage() {
        System.out.println(USAGE);
    }

    private static Map<String, AbstractCliApp> createApplications() {
        SciencePlanRepository plans = new FileSciencePlanRepository();
        ObservingProgramRepository programs = new FileObservingProgramRepository(plans);
        StarCatalog catalog = new SimulatedStarCatalog();
        PlanValidator validator = new PlanValidator(List.of(
                new PlanIdPositiveRule(),
                new PlanNameNotBlankRule(),
                new CreatorNotBlankRule(),
                new FundingNotBlankRule(),
                new ObjectiveNotBlankRule(),
                new QuadrantTelescopeConsistencyRule(),
                new ObservationMonthRule()));
        Map<String, AbstractCliApp> apps = new LinkedHashMap<>();
        apps.put("create-plan",
                new CreateSciencePlanApp(new CreateSciencePlanService(catalog, plans)));
        apps.put("mark-tested",
                new MarkPlanTestedApp(plans));
        apps.put("submit-plan",
                new SubmitSciencePlanApp(new SubmitSciencePlanService(plans)));
        apps.put("validate-plan",
                new ValidateSciencePlanApp(
                        new ValidateSciencePlanService(plans, validator)));
        apps.put("create-program",
                new CreateObservingProgramApp(
                        new CreateObservingProgramService(plans, programs)));
        return apps;
    }
}
