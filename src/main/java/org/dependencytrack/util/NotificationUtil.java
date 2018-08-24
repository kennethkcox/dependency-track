/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.util;

import alpine.notification.Notification;
import alpine.notification.NotificationLevel;
import org.dependencytrack.model.Analysis;
import org.dependencytrack.model.AnalysisState;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.Dependency;
import org.dependencytrack.model.Project;
import org.dependencytrack.model.Tag;
import org.dependencytrack.model.Vulnerability;
import org.dependencytrack.notification.NotificationConstants;
import org.dependencytrack.notification.NotificationGroup;
import org.dependencytrack.notification.NotificationScope;
import org.dependencytrack.notification.vo.NewVulnerabilityIdentified;
import org.dependencytrack.persistence.QueryManager;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationUtil {

    public static void analyzeNotificationCriteria(Vulnerability vulnerability, Component component) {
        QueryManager qm = new QueryManager();
        if (!qm.contains(vulnerability, component)) {
            // Component did not previously contain this vulnerability. It could be a newly discovered vulnerability
            // against an existing component, or it could be a newly added (and vulnerable) component. Either way,
            // it warrants a Notification be dispatched.
            Set<Project> affectedProjects = new HashSet<>();
            List<Dependency> dependencies = qm.getAllDependencies(component);
            for (Dependency dependency : dependencies) {
                affectedProjects.add(dependency.getProject());
            }
            Notification.dispatch(new Notification()
                    .scope(NotificationScope.PORTFOLIO)
                    .group(NotificationGroup.NEW_VULNERABILITY)
                    .title(NotificationConstants.Title.NEW_VULNERABILITY)
                    .level(NotificationLevel.INFORMATIONAL)
                    .subject(new NewVulnerabilityIdentified(vulnerability, component, affectedProjects))
            );
        }
    }

    public static void analyzeNotificationCriteria(QueryManager qm, Dependency newDependency) {
        Dependency dependency = qm.getDependency(newDependency);
        List<Vulnerability> vulnerabilities = qm.getAllVulnerabilities(dependency);
        for (Vulnerability vulnerability: vulnerabilities) {
            Set<Project> affectedProjects = new HashSet<>(Collections.singletonList(dependency.getProject()));
            Notification.dispatch(new Notification()
                    .scope(NotificationScope.PORTFOLIO)
                    .group(NotificationGroup.NEW_VULNERABILITY)
                    .title(NotificationConstants.Title.NEW_VULNERABLE_DEPENDENCY)
                    .level(NotificationLevel.INFORMATIONAL)
                    .subject(new NewVulnerabilityIdentified(vulnerability, dependency.getComponent(), affectedProjects))
            );
        }
    }

    public static void analyzeNotificationCriteria(QueryManager qm, Analysis analysis, boolean hasChanged) {
        if (AnalysisState.EXPLOITABLE == analysis.getAnalysisState() && hasChanged) {
            final NotificationGroup notificationGroup;
            final Set<Project> affectedProjects = new HashSet<>();
            if (analysis.getProject() != null) {
                // This was an analysis decision affecting a single project
                notificationGroup = NotificationGroup.PROJECT_AUDIT_CHANGE;
                affectedProjects.add(analysis.getProject());
            } else {
                // This was a global analysis decision affecting all projects
                notificationGroup = NotificationGroup.GLOBAL_AUDIT_CHANGE;
                List<Dependency> dependencies = qm.getAllDependencies(analysis.getProject());
                for (Dependency dependency : dependencies) {
                    affectedProjects.add(dependency.getProject());
                }
            }
            Notification.dispatch(new Notification()
                    .scope(NotificationScope.PORTFOLIO)
                    .group(notificationGroup)
                    .title(NotificationConstants.Title.EXPLOITABLE_ANALYSIS_DECISION)
                    .level(NotificationLevel.INFORMATIONAL)
                    .subject(new NewVulnerabilityIdentified(analysis.getVulnerability(), analysis.getComponent(), affectedProjects))
            );
        }
    }

    public static JsonObject toJson(Project project) {
        JsonObjectBuilder projectBuilder = Json.createObjectBuilder();
        projectBuilder.add("uuid", project.getUuid().toString());
        JsonUtil.add(projectBuilder, "name", project.getName());
        JsonUtil.add(projectBuilder, "version", project.getVersion());
        JsonUtil.add(projectBuilder, "description", project.getDescription());
        JsonUtil.add(projectBuilder, "purl", project.getPurl());
        if (project.getTags() != null && project.getTags().size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (Tag tag: project.getTags()) {
                sb.append(tag.getName()).append(",");
            }
            String tags = sb.toString();
            if (tags.endsWith(",")) {
                tags = tags.substring(0, tags.length()-1);
            }
            JsonUtil.add(projectBuilder, "tags", tags);
        }
        return projectBuilder.build();
    }

    public static JsonObject toJson(Component component) {
        JsonObjectBuilder componentBuilder = Json.createObjectBuilder();
        componentBuilder.add("uuid", component.getUuid().toString());
        JsonUtil.add(componentBuilder, "group", component.getGroup());
        JsonUtil.add(componentBuilder, "name", component.getName());
        JsonUtil.add(componentBuilder, "version", component.getVersion());
        JsonUtil.add(componentBuilder, "md5", component.getMd5());
        JsonUtil.add(componentBuilder, "sha1", component.getSha1());
        JsonUtil.add(componentBuilder, "sha256", component.getSha256());
        JsonUtil.add(componentBuilder, "sha512", component.getSha512());
        if (component.getPurl() != null) {
            componentBuilder.add("purl", component.getPurl().canonicalize());
        }
        return componentBuilder.build();
    }

    public static JsonObject toJson(Vulnerability vulnerability) {
        JsonObjectBuilder vulnerabilityBuilder = Json.createObjectBuilder();
        vulnerabilityBuilder.add("uuid", vulnerability.getUuid().toString());
        JsonUtil.add(vulnerabilityBuilder, "vulnId", vulnerability.getVulnId());
        JsonUtil.add(vulnerabilityBuilder, "source", vulnerability.getSource());
        JsonUtil.add(vulnerabilityBuilder, "title", vulnerability.getTitle());
        JsonUtil.add(vulnerabilityBuilder, "subtitle", vulnerability.getSubTitle());
        JsonUtil.add(vulnerabilityBuilder, "description", vulnerability.getDescription());
        JsonUtil.add(vulnerabilityBuilder, "recommendation", vulnerability.getRecommendation());
        JsonUtil.add(vulnerabilityBuilder, "cvssv2", vulnerability.getCvssV2BaseScore());
        JsonUtil.add(vulnerabilityBuilder, "cvssv3", vulnerability.getCvssV3BaseScore());
        JsonUtil.add(vulnerabilityBuilder, "severity",  vulnerability.getSeverity());
        if (vulnerability.getCwe() != null) {
            JsonObject cweNode = Json.createObjectBuilder()
                    .add("cweId", vulnerability.getCwe().getCweId())
                    .add("name", vulnerability.getCwe().getName())
                    .build();
            vulnerabilityBuilder.add("cwe", cweNode);
        }
        return vulnerabilityBuilder.build();
    }

    public static JsonObject toJson(NewVulnerabilityIdentified vo) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (vo.getComponent() != null) {
            builder.add("component", toJson(vo.getComponent()));
        }
        if (vo.getVulnerability() != null) {
            builder.add("vulnerability", toJson(vo.getVulnerability()));
        }
        if (vo.getAffectedProjects() != null && vo.getAffectedProjects().size() > 0) {
            JsonArrayBuilder projectsBuilder = Json.createArrayBuilder();
            for (Project project: vo.getAffectedProjects()) {
                projectsBuilder.add(toJson(project));
            }
            builder.add("affectedProjects", projectsBuilder.build());
        }
        return builder.build();
    }

}
