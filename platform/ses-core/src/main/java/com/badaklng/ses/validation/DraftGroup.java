package com.badaklng.ses.validation;

import javax.validation.groups.Default;

/**
 * Validation group for draft saves.
 * Enforces minimal validation (e.g. form identity) allowing incomplete input to be saved.
 */
public interface DraftGroup extends Default {
}
