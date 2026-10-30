UPDATE organizations SET settings=settings || '{"reviewThreshold":"100000.00"}'::jsonb WHERE NOT(settings ? 'reviewThreshold');
