-- 1. Seed Roles
INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'System administrator with full access to all system resources and settings'),
    ('TRAVEL_MANAGER', 'Travel manager responsible for approving bookings, managing travel policies, and reviewing reports'),
    ('TRAVELER', 'Corporate traveler who can view policies, submit booking requests, and manage personal profile')
ON CONFLICT (name) DO NOTHING;

-- 2. Seed Default Permissions
INSERT INTO permissions (name, description) VALUES
    -- User management permissions
    ('users:read', 'View user profiles and account statuses'),
    ('users:write', 'Create and modify user profiles'),
    ('users:delete', 'Deactivate or delete user accounts'),
    
    -- Role & Permission management
    ('roles:read', 'View roles and their assigned permissions'),
    ('roles:assign', 'Assign or revoke roles to/from users'),
    
    -- Trip & Booking management permissions
    ('trips:read', 'View trip bookings and itinerary details'),
    ('trips:create', 'Create new trip requests and bookings'),
    ('trips:update', 'Update existing trip bookings'),
    ('trips:cancel', 'Cancel trip bookings'),
    ('trips:approve', 'Approve or reject employee trip requests'),
    
    -- Travel Policy management permissions
    ('policies:read', 'View corporate travel policies and compliance guidelines'),
    ('policies:write', 'Create and modify travel policy rules and limits'),
    
    -- Expense management permissions
    ('expenses:read', 'View expense reports and receipts'),
    ('expenses:create', 'Submit new expense reports'),
    ('expenses:approve', 'Approve or reject expense reports'),
    
    -- Reporting & Analytics
    ('reports:view', 'View organizational travel spend and audit reports')
ON CONFLICT (name) DO NOTHING;

-- 3. Seed Role-Permission Associations
-- ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TRAVEL_MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'users:read',
    'roles:read',
    'trips:read',
    'trips:create',
    'trips:update',
    'trips:cancel',
    'trips:approve',
    'policies:read',
    'expenses:read',
    'expenses:create',
    'expenses:approve',
    'reports:view'
)
WHERE r.name = 'TRAVEL_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TRAVELER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'trips:read',
    'trips:create',
    'trips:update',
    'trips:cancel',
    'policies:read',
    'expenses:read',
    'expenses:create'
)
WHERE r.name = 'TRAVELER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
