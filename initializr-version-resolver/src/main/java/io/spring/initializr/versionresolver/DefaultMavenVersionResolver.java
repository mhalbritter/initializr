/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package io.spring.initializr.versionresolver;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectModelResolver;
import org.apache.maven.repository.supplier.RepositorySystemSupplier;
import org.apache.maven.repository.supplier.SessionBuilderSupplier;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;
import org.jspecify.annotations.Nullable;

/**
 * A {@link MavenVersionResolver} that resolves versions using Maven Resolver. Maven's
 * default {@link LocalRepositoryManager} implementation is not thread-safe. To avoid
 * corruption of the local repository, interaction with the {@link RepositorySystem} is
 * single-threaded.
 *
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 */
class DefaultMavenVersionResolver implements MavenVersionResolver {

	private static final Log logger = LogFactory.getLog(DefaultMavenVersionResolver.class);

	private static final RemoteRepository mavenCentral = new RemoteRepository.Builder("central", "default",
			"https://repo1.maven.org/maven2")
		.build();

	private static final RemoteRepository springMilestones = new RemoteRepository.Builder("spring-milestones",
			"default", "https://repo.spring.io/milestone")
		.build();

	private static final RemoteRepository springSnapshots = new RemoteRepository.Builder("spring-snapshots", "default",
			"https://repo.spring.io/snapshot")
		.build();

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private final Object monitor = new Object();

	private final CloseableSession repositorySystemSession;

	private final RemoteRepositoryManager remoteRepositoryManager;

	private final RepositorySystem repositorySystem;

	private final List<RemoteRepository> repositories;

	DefaultMavenVersionResolver(Path cacheLocation) {
		this(cacheLocation, null);
	}

	// Routes Maven Central through the mirror, if any, e.g. in a network without access
	// to Maven Central
	DefaultMavenVersionResolver(Path cacheLocation, @Nullable RemoteRepository centralMirror) {
		this.repositories = List.of((centralMirror != null) ? centralMirror : mavenCentral, springMilestones,
				springSnapshots);
		RepositorySystemSupplier repositorySystemSupplier = new RepositorySystemSupplier();
		this.repositorySystem = repositorySystemSupplier.get();
		SessionBuilder sessionBuilder = new SessionBuilderSupplier(this.repositorySystem).get()
			.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(false, false))
			.withLocalRepositoryBaseDirectories(cacheLocation)
			.setUserProperties(System.getProperties())
			.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT.toMillis())
			.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, REQUEST_TIMEOUT.toMillis());
		if (centralMirror != null) {
			mirrorCentral(sessionBuilder, centralMirror);
		}
		this.repositorySystemSession = sessionBuilder.build();
		this.remoteRepositoryManager = repositorySystemSupplier.getRemoteRepositoryManager();
	}

	// Maven's super POM and resolved POMs declare Central too, with id 'central'.
	// Mirror it, like <mirrorOf>central</mirrorOf> in settings.xml.
	private static void mirrorCentral(SessionBuilder sessionBuilder, RemoteRepository mirror) {
		sessionBuilder.setMirrorSelector(new DefaultMirrorSelector().add(mirror.getId(), mirror.getUrl(),
				mirror.getContentType(), false, false, mavenCentral.getId(), "*"));
		sessionBuilder.setAuthenticationSelector(
				new DefaultAuthenticationSelector().add(mirror.getId(), mirror.getAuthentication()));
	}

	@Override
	public Map<String, String> resolveDependencies(String groupId, String artifactId, String version) {
		ArtifactDescriptorResult bom = resolveBom(groupId, artifactId, version);
		Map<String, String> managedVersions = new HashMap<>();
		bom.getManagedDependencies()
			.stream()
			.map(Dependency::getArtifact)
			.forEach((artifact) -> managedVersions.putIfAbsent(artifact.getGroupId() + ":" + artifact.getArtifactId(),
					artifact.getVersion()));
		return managedVersions;
	}

	@Override
	public Map<String, String> resolvePlugins(String groupId, String artifactId, String version) {
		Model model = buildEffectiveModel(groupId, artifactId, version);
		Map<String, String> managedPluginVersions = new HashMap<>();
		model.getBuild()
			.getPluginManagement()
			.getPlugins()
			.forEach((plugin) -> managedPluginVersions.putIfAbsent(plugin.getGroupId() + ":" + plugin.getArtifactId(),
					plugin.getVersion()));
		return managedPluginVersions;
	}

	@Override
	public void close() {
		synchronized (this.monitor) {
			this.repositorySystemSession.close();
			this.repositorySystem.shutdown();
		}
	}

	private ArtifactDescriptorResult resolveBom(String groupId, String artifactId, String version) {
		synchronized (this.monitor) {
			try {
				return this.repositorySystem.readArtifactDescriptor(this.repositorySystemSession,
						new ArtifactDescriptorRequest(new DefaultArtifact(groupId, artifactId, "pom", version),
								this.repositories, null));
			}
			catch (ArtifactDescriptorException ex) {
				throw new IllegalStateException(
						"Bom '" + groupId + ":" + artifactId + ":" + version + "' could not be resolved", ex);
			}
		}
	}

	private Model buildEffectiveModel(String groupId, String artifactId, String version) {
		try {
			ArtifactResult bom = resolvePom(groupId, artifactId, version);
			RequestTrace requestTrace = new RequestTrace(null);

			ModelResolver modelResolver = new ProjectModelResolver(this.repositorySystemSession, requestTrace,
					this.repositorySystem, this.remoteRepositoryManager, this.repositories,
					ProjectBuildingRequest.RepositoryMerging.POM_DOMINANT, null);
			DefaultModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest();
			modelBuildingRequest.setSystemProperties(System.getProperties());
			modelBuildingRequest.setPomFile(bom.getArtifact().getPath().toFile());
			modelBuildingRequest.setModelResolver(modelResolver);
			DefaultModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
			return modelBuilder.build(modelBuildingRequest).getEffectiveModel();
		}
		catch (ModelBuildingException ex) {
			Model model = ex.getModel();
			if (model != null) {
				logger.warn("Model for '" + groupId + ":" + artifactId + ":" + version + "' is incomplete: "
						+ ex.getProblems());
				return model;
			}
			throw new IllegalStateException(
					"Model for '" + groupId + ":" + artifactId + ":" + version + "' could not be built", ex);
		}
	}

	private ArtifactResult resolvePom(String groupId, String artifactId, String version) {
		synchronized (this.monitor) {
			try {
				return this.repositorySystem.resolveArtifact(this.repositorySystemSession, new ArtifactRequest(
						new DefaultArtifact(groupId, artifactId, "pom", version), this.repositories, null));
			}
			catch (ArtifactResolutionException ex) {
				throw new IllegalStateException(
						"Pom '" + groupId + ":" + artifactId + ":" + version + "' could not be resolved", ex);
			}
		}
	}

}
