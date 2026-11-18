package com.quell.assistant;

import com.quell.domain.Employee;
import com.quell.intelligence.CategoryClassifier;
import com.quell.intelligence.CategorySuggester;
import com.quell.intelligence.ResolutionRecommender;
import com.quell.intelligence.ServiceDetector;
import com.quell.intelligence.TriageAdvisor;
import com.quell.repository.CategoryRepository;
import com.quell.repository.ITServiceRepository;
import com.quell.repository.IncidentRepository;
import com.quell.service.IncidentService;
import com.quell.status.StatusService;

/** What the steps of a conversation may use: who is talking, and the services that do the work. */
record AssistantContext(Employee employee, IncidentService incidentService, IncidentRepository incidents,
                        ITServiceRepository services, CategoryRepository categories, ServiceDetector serviceDetector,
                        TriageAdvisor triage, CategorySuggester categorySuggester, CategoryClassifier classifier,
                        ResolutionRecommender recommender, StatusService statusService) {
}
