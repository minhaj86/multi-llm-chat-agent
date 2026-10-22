ALTER TABLE conversations
    ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN audio_settings JSONB,
    ADD CONSTRAINT chk_conversations_type CHECK (type IN ('TEXT', 'AUDIO')),
    ADD CONSTRAINT chk_conversations_audio_settings CHECK (
        (type = 'TEXT' AND audio_settings IS NULL) OR
        (type = 'AUDIO' AND audio_settings IS NOT NULL)
    );
