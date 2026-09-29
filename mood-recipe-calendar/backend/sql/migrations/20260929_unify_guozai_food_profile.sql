ALTER TABLE agent_memory_facts
  ADD COLUMN memory_category VARCHAR(32) NOT NULL DEFAULT 'EXPLICIT_PREFERENCE' AFTER memory_value;

UPDATE agent_memory_facts SET memory_category = 'SAFETY_CONSTRAINT'
WHERE memory_key LIKE 'safety.%';
UPDATE agent_memory_facts SET memory_category = 'BEHAVIOR_SIGNAL'
WHERE memory_key LIKE 'affinity.%' OR memory_key LIKE 'dish.%' OR memory_key LIKE 'strategy.%';
UPDATE agent_memory_facts SET memory_category = 'MEAL_CONTEXT'
WHERE memory_key IN ('people','dishesPerDay','cookingDays','household','budget');
UPDATE agent_memory_facts SET memory_category = 'SHORT_TERM_STATE'
WHERE expires_at IS NOT NULL AND memory_category <> 'SAFETY_CONSTRAINT';
