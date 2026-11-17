package com.itsm.assistant;

import com.itsm.domain.Employee;
import com.itsm.intelligence.CategoryClassifier;
import com.itsm.intelligence.CategorySuggester;
import com.itsm.intelligence.ResolutionRecommender;
import com.itsm.intelligence.ServiceDetector;
import com.itsm.intelligence.TriageAdvisor;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.service.IncidentService;
import com.itsm.status.StatusService;

/** What the steps of a conversation may use: who is talking, and the services that do the work. */
record AssistantContext(Employee employee, IncidentService incidentService, IncidentRepository incidents,
                        ITServiceRepository services, CategoryRepository categories, ServiceDetector serviceDetector,
                        TriageAdvisor triage, CategorySuggester categorySuggester, CategoryClassifier classifier,
                        ResolutionRecommender recommender, StatusService statusService) {
}
