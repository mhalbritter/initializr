/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package io.spring.initializr.versionresolver;

import java.nio.file.Path;
import java.util.Map;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link DefaultMavenVersionResolver}.
 *
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 */
class DefaultMavenVersionResolverTests {

	// Maven mirror env, e.g. set by CI
	private static final String MIRROR_URL_ENV = "MAVEN_MIRROR_URL";

	private static final String MIRROR_USERNAME_ENV = "MAVEN_MIRROR_USERNAME";

	private static final String MIRROR_PASSWORD_ENV = "MAVEN_MIRROR_PASSWORD";

	private MavenVersionResolver resolver;

	@BeforeEach
	void createResolver(@TempDir Path temp) {
		String mirrorUrl = System.getenv(MIRROR_URL_ENV);
		if (mirrorUrl == null || mirrorUrl.isBlank()) {
			this.resolver = new DefaultMavenVersionResolver(temp);
			return;
		}

		this.resolver = new DefaultMavenVersionResolver(temp, mirror(mirrorUrl));
	}

	private static RemoteRepository mirror(String url) {
		AuthenticationBuilder authentication = new AuthenticationBuilder()
			.addUsername(System.getenv(MIRROR_USERNAME_ENV))
			.addPassword(System.getenv(MIRROR_PASSWORD_ENV));
		return new RemoteRepository.Builder("maven-mirror", "default", url).setAuthentication(authentication.build())
			.build();
	}

	@AfterEach
	void closeResolver() {
		this.resolver.close();
	}

	@Test
	void resolveDependenciesAfterCloseFails() {
		this.resolver.close();
		assertThatIllegalStateException().isThrownBy(() -> this.resolver.resolveDependencies("org.springframework.boot",
				"spring-boot-dependencies", "3.4.1"));
	}

	@Test
	void resolveDependenciesForSpringBoot() {
		Map<String, String> versions = this.resolver.resolveDependencies("org.springframework.boot",
				"spring-boot-dependencies", "3.4.1");
		assertThat(versions).containsEntry("io.micrometer:micrometer-core", "1.14.2")
			.containsEntry("org.springframework.boot:spring-boot-autoconfigure", "3.4.1")
			.containsEntry("org.junit.jupiter:junit-jupiter-api", "5.11.4");
	}

	@Test
	void resolveDependenciesForSpringCloud() {
		Map<String, String> versions = this.resolver.resolveDependencies("org.springframework.cloud",
				"spring-cloud-dependencies", "2024.0.0");
		assertThat(versions).containsEntry("com.netflix.eureka:eureka-client", "2.0.4");
	}

	@Test
	void resolveDependenciesUsingMilestones() {
		Map<String, String> versions = this.resolver.resolveDependencies("org.springframework.boot",
				"spring-boot-dependencies", "3.4.0-M1");
		assertThat(versions).containsEntry("org.flywaydb:flyway-core", "10.15.2");
	}

	@Test
	void resolveDependenciesUsingSnapshots() {
		Map<String, String> versions = this.resolver.resolveDependencies("org.springframework.boot",
				"spring-boot-dependencies", "3.4.0-SNAPSHOT");
		assertThat(versions).isNotEmpty();
	}

	@Test
	void resolveDependenciesForNonExistentDependency() {
		assertThatIllegalStateException()
			.isThrownBy(() -> this.resolver.resolveDependencies("org.springframework.boot", "spring-boot-bom", "1.0"))
			.withMessage("Bom 'org.springframework.boot:spring-boot-bom:1.0' could not be resolved");
	}

	@Test
	void resolvePluginsForSpringBoot() {
		Map<String, String> versions = this.resolver.resolvePlugins("org.springframework.boot",
				"spring-boot-starter-parent", "3.4.1");
		assertThat(versions).containsEntry("org.springframework.boot:spring-boot-maven-plugin", "3.4.1");
	}

	@Test
	void resolvePluginsUsingMilestones() {
		Map<String, String> versions = this.resolver.resolvePlugins("org.springframework.boot",
				"spring-boot-dependencies", "3.4.0-M1");
		assertThat(versions).containsEntry("org.springframework.boot:spring-boot-maven-plugin", "3.4.0-M1");
	}

	@Test
	void resolvePluginsUsingMilestoneThatHasResolutionProblem() {
		Map<String, String> versions = this.resolver.resolvePlugins("org.springframework.boot",
				"spring-boot-dependencies", "3.0.0-M1");
		assertThat(versions).containsEntry("org.springframework.boot:spring-boot-maven-plugin", "3.0.0-M1");
	}

	@Test
	void resolvePluginsUsingSnapshots() {
		Map<String, String> versions = this.resolver.resolvePlugins("org.springframework.boot",
				"spring-boot-dependencies", "3.4.0-SNAPSHOT");
		assertThat(versions).isNotEmpty();
	}

	@Test
	void resolvePluginsForNonExistentDependency() {
		assertThatIllegalStateException()
			.isThrownBy(() -> this.resolver.resolvePlugins("org.springframework.boot", "spring-boot-bom", "1.0"))
			.withMessage("Pom 'org.springframework.boot:spring-boot-bom:1.0' could not be resolved");
	}

}
