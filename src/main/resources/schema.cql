create table if not exists demo.policy (id int primary key, policy text, policy_vector vector<float,1536>);
create custom index if not exists ann_policy_index on demo.policy(policy_vector) using 'StorageAttachedIndex';
