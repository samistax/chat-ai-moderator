create table if not exists demo.policy (id int primary key, policy text, policy_vector vector<float,1536>, policy_vector_dot vector<float,1536>);
create custom index if not exists ann_policy_index on demo.policy(policy_vector) using 'StorageAttachedIndex');
create custom index if not exists ann_policy_dot_index on demo.policy(policy_vector_dot) using 'StorageAttachedIndex' WITH OPTIONS = {'similarity_function' : 'dot_product'});


CREATE CUSTOM INDEX ON chat_message(userid) USING 'org.apache.cassandra.index.sai.StorageAttachedIndex';