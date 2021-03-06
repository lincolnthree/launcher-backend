package io.fabric8.launcher.osio;

import java.net.URL;
import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import io.fabric8.launcher.core.api.Boom;
import io.fabric8.launcher.core.api.ImmutableBoom;
import io.fabric8.launcher.core.api.MissionControl;
import io.fabric8.launcher.core.api.Projectile;
import io.fabric8.launcher.core.api.ProjectileContext;
import io.fabric8.launcher.core.spi.Application;
import io.fabric8.launcher.osio.projectiles.ImmutableOsioProjectile;
import io.fabric8.launcher.osio.projectiles.ImportProjectile;
import io.fabric8.launcher.osio.projectiles.OsioProjectile;
import io.fabric8.launcher.osio.projectiles.OsioProjectileContext;
import io.fabric8.launcher.osio.steps.GitSteps;
import io.fabric8.launcher.osio.steps.OpenShiftSteps;
import io.fabric8.launcher.service.git.api.GitRepository;
import io.fabric8.launcher.service.openshift.api.OpenShiftProject;
import io.fabric8.launcher.service.openshift.api.OpenShiftResource;

import static io.fabric8.launcher.core.spi.Application.ApplicationType.LAUNCHER;
import static io.fabric8.launcher.core.spi.Application.ApplicationType.OSIO;

/**
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
@Application(OSIO)
@RequestScoped
public class OsioMissionControl implements MissionControl {

    @Inject
    @Application(LAUNCHER)
    private MissionControl missionControl;

    @Inject
    private GitSteps gitSteps;

    @Inject
    private OpenShiftSteps openShiftSteps;

    @Override
    public Projectile prepare(ProjectileContext genericContext) {
        if (!(genericContext instanceof OsioProjectileContext)) {
            throw new IllegalArgumentException("OsioMissionControl only supports " + OsioProjectileContext.class.getName() + " instances");
        }
        OsioProjectileContext context = (OsioProjectileContext) genericContext;
        Projectile projectile = missionControl.prepare(context);
        return ImmutableOsioProjectile.builder()
                .from(projectile)
                .spacePath(context.getSpacePath())
                .pipelineId(context.getPipelineId())
                .build();
    }

    @Override
    public Boom launch(Projectile genericProjectile) throws IllegalArgumentException {
        if (!(genericProjectile instanceof OsioProjectile)) {
            throw new IllegalArgumentException("OsioMissionControl only supports " + OsioProjectile.class.getName() + " instances");
        }
        OsioProjectile projectile = (OsioProjectile) genericProjectile;
        GitRepository repository = gitSteps.createRepository(projectile);

        openShiftSteps.createBuildConfig(projectile, repository);
        openShiftSteps.createJenkinsConfigMap(repository);

        // create webhook first so that push will trigger build
        gitSteps.createWebHooks(projectile, repository);
        gitSteps.pushToGitRepository(projectile, repository);

        gitSteps.pushToGitRepository(projectile, repository);

        openShiftSteps.triggerBuild(projectile);
        return ImmutableBoom.builder()
                .createdRepository(repository)
                .createdProject(new OpenShiftProject() {
                    @Override
                    public String getName() {
                        return projectile.getOpenShiftProjectName();
                    }

                    @Override
                    public URL getConsoleOverviewUrl() {
                        return null;
                    }

                    @Override
                    public List<OpenShiftResource> getResources() {
                        return null;
                    }
                })
                .build();
    }


    public Boom launch(ImportProjectile importProjectile) {
        return null;
    }
}
