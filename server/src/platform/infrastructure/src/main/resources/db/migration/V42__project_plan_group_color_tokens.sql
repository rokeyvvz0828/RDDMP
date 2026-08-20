-- Normalize legacy plan group theme keys to semantic brand color tokens.
UPDATE pm_project_plan_group
SET color_key = 'brand'
WHERE color_key IS NULL
   OR color_key = ''
   OR color_key IN ('ocean', 'emerald', 'sunset', 'graphite', 'tech-blue', 'violet', 'amber');
