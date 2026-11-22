package io.lytgos.provider;

import io.lytgos.core.Profile;

/**
 * LLM provider abstraction. Implementations map a profile to a concrete chat model.
 *
 * @author LytgOS2 Team
 */
@FunctionalInterface
public interface ProviderService {

  /**
   * Sends a prompt to the provider.
   *
   * @param profile agent profile
   * @param prompt user prompt
   * @return model response
   */
  String chat(Profile profile, String prompt);
}
