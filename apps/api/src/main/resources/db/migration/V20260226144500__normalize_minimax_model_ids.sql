UPDATE user_ai_settings
SET topic_extraction_model = 'abab6.5g-chat',
    updated_at = NOW()
WHERE topic_extraction_provider = 'minimax'
  AND topic_extraction_model = 'MiniMax-M2.5';

UPDATE user_ai_settings
SET source_formatting_model = 'abab6.5g-chat',
    updated_at = NOW()
WHERE source_formatting_provider = 'minimax'
  AND source_formatting_model = 'MiniMax-M2.5';
