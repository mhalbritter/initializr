/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.initializr.generator.spring.code.kotlin;

import io.spring.initializr.generator.buildsystem.gradle.GradleBuild;
import io.spring.initializr.generator.spring.build.BuildCustomizer;

/**
 * {@link BuildCustomizer} for Kotlin projects build with Gradle.
 *
 * @author Andy Wilkinson
 * @author Jean-Baptiste Nizet
 * @author Moritz Halbritter
 */
class KotlinGradleBuildCustomizer implements BuildCustomizer<GradleBuild> {

	private final KotlinProjectSettings settings;

	KotlinGradleBuildCustomizer(KotlinProjectSettings kotlinProjectSettings) {
		this.settings = kotlinProjectSettings;
	}

	@Override
	public void customize(GradleBuild build) {
		build.plugins().add("org.jetbrains.kotlin.jvm", (plugin) -> plugin.setVersion(this.settings.getVersion()));
		build.plugins()
			.add("org.jetbrains.kotlin.plugin.spring", (plugin) -> plugin.setVersion(this.settings.getVersion()));
		customizeCompilerOptions(build);
	}

	private void customizeCompilerOptions(GradleBuild build) {
		build.extensions().customize("kotlin", (kotlin) -> kotlin.nested("compilerOptions", (compilerOptions) -> {
			compilerOptions.attributeWithType("jvmTarget", getJvmTarget(), "org.jetbrains.kotlin.gradle.dsl.JvmTarget");
			for (String compilerArg : this.settings.getCompilerArgs()) {
				compilerOptions.append("freeCompilerArgs", "'" + compilerArg + "'");
			}
		}));
	}

	private String getJvmTarget() {
		return switch (this.settings.getJvmTarget()) {
			case "1.8" -> "JvmTarget.JVM_1_8";
			default -> "JvmTarget.JVM_" + this.settings.getJvmTarget();
		};
	}

}
