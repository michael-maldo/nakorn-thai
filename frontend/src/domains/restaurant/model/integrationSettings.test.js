import {test} from 'node:test';
import assert from 'node:assert/strict';
import {definitions,booleanFields,dependencyWarnings,replacementBody} from './integrationSettings.js';
const config={PAYPAL:{configured:false,fields:{environment:'sandbox'}},PAYID:{configured:false},TWILIO:{smsVerificationConfigured:false,smsSendingConfigured:false},SMTP:{configured:false}};
test('business fields use owner language and provider secrets are separated',()=>{
 assert.equal(definitions.SETTINGS.fields.paypalEnabled,'Enable PayPal checkout');assert.ok(booleanFields.has('reservationsEnabled'));
 assert.deepEqual(Object.keys(definitions.PAYPAL.secrets),['clientSecret']);assert.ok(!definitions.PAYPAL.fields.clientSecret);
 assert.equal(definitions.PAYID.fields.accountName,'Recipient/account name');assert.equal(definitions.SMTP.secrets.password,'SMTP password');
});
test('blank secrets preserve credentials and replacement never uses masked values',()=>{
 const body=replacementBody(4,{clientId:'merchant'},{clientSecret:'',password:'   ',authToken:'new-test-token'});
 assert.deepEqual(body.secrets,{authToken:'new-test-token'});assert.equal(body.version,4);assert.deepEqual(body.clearSecrets,[]);
 assert.deepEqual(replacementBody(5,{}, {},['clientSecret']).clearSecrets,['clientSecret']);
});
test('dependency warnings cover payment verification SMS and optional email',()=>{
 const fields={paypalEnabled:'true',payidEnabled:'true',orderPhoneRequired:'true',reservationPhoneRequired:'true',orderSms:'true',reservationSms:'true',orderEmail:'true',reservationEmail:'true'};
 assert.equal(dependencyWarnings(fields,config).length,5);
 assert.deepEqual(dependencyWarnings(fields,{PAYPAL:{configured:true},PAYID:{configured:true},TWILIO:{smsVerificationConfigured:true,smsSendingConfigured:true},SMTP:{configured:true}}),[]);
});
test('online ordering requires payment and live credentials require successful validation',()=>{
 assert.match(dependencyWarnings({orderingEnabled:'true'},config).join(' '),/available payment method/);
 const live={...config,PAYPAL:{configured:true,fields:{environment:'live'},validationStatus:'NOT_TESTED'}};
 assert.match(dependencyWarnings({paypalEnabled:'true'},live).join(' '),/Test live PayPal/);
 assert.deepEqual(dependencyWarnings({paypalEnabled:'true'},{...live,PAYPAL:{...live.PAYPAL,validationStatus:'VALID'}}),[]);
});
test('SMS cannot be offered to unverified contacts and disabling features clears warnings',()=>{
 assert.match(dependencyWarnings({orderSms:'true',orderPhoneRequired:'false'},config).join(' '),/verified mobile/);
 assert.deepEqual(dependencyWarnings(Object.fromEntries(Object.keys(definitions.SETTINGS.fields).map(k=>[k,'false'])),config),[]);
});
