package io.lytgos.provider;

import io.lytgos.core.Profile;

/**
 * LLM provider abstraction. Implementations map a profile to a concrete chat model.
 */
public interface ProviderService {

    String chat(Profile profile, String prompt);
}
