package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.Intent;

import java.util.List;

public interface AnswerGenerator {
    AgentAnswer generate(String question, List<Evidence> evidence, Intent intent);
}
