import {test} from 'node:test';
import assert from 'node:assert/strict';
import {savedDraft, settingsDraftDirty} from './settingsDraft.js';
test('Settings dirty detection compares values, not key order or metadata',()=>{
 const row={fields:{host:'saved',port:'587'},version:2};
 const draft=savedDraft(row);
 assert.equal(settingsDraftDirty(draft,row),false);
 draft.fields={port:'587',host:'saved'};
 assert.equal(settingsDraftDirty(draft,{...row,version:3}),false);
 draft.fields.host='edit';assert.equal(settingsDraftDirty(draft,row),true);
 assert.equal(row.fields.host,'saved');
 assert.deepEqual(savedDraft(row).fields,row.fields);
});
test('write-only inputs and explicit clear are dirty; opening replacement alone is not',()=>{
 const row={fields:{clientId:'saved'}};const draft=savedDraft(row);
 draft.replace.clientSecret=true;assert.equal(settingsDraftDirty(draft,row),false);
 draft.secrets.clientSecret='secret';assert.equal(settingsDraftDirty(draft,row),true);
 draft.secrets={clientSecret:''};assert.equal(settingsDraftDirty(draft,row),false);
 draft.clear=['clientSecret'];assert.equal(settingsDraftDirty(draft,row),true);
 assert.deepEqual(savedDraft(row).secrets,{});assert.deepEqual(savedDraft(row).clear,[]);
});
