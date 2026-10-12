// Presentation accepts national and Australian international mobile entry.
// Invalid/foreign input is retained so validation never silently changes its meaning.
export function australianMobilePayload(value) {
 const compact=value.trim().replace(/[ ()-]/g,'');
 if (/^04[0-9]*$/.test(compact)) return '+61'+compact.slice(1);
 if (/^4[0-9]*$/.test(compact)) return '+61'+compact;
 if (/^(?:\+61|61)0?4[0-9]*$/.test(compact)) return '+61'+compact.replace(/^(?:\+61|61)0?/, '');
 return compact;
}
export function validAustralianMobile(value) {return /^\+614[0-9]{8}$/.test(australianMobilePayload(value));}
export function australianMobileDisplay(value) {
 const payload=australianMobilePayload(value);
 if (!/^\+614[0-9]{0,8}$/.test(payload)) return value;
 return '+61 '+payload.slice(3).match(/.{1,3}/g).join(' ');
}
