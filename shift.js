// OT is stored in whole minutes. Older records only have decimal hours in "ot".
function otMinutesOf(entry) {
    if (Number.isInteger(entry.otMinutes) && entry.otMinutes >= 0 && entry.otMinutes <= 1440) {
        return entry.otMinutes;
    }
    const hours = Number(entry.ot);
    return Number.isFinite(hours) && hours >= 0 ? Math.round(hours * 60) : 0;
}

function minuteClock(minutes) {
    return Math.floor(minutes / 60) + ':' + String(minutes % 60).padStart(2, '0');
}

function clockMinute(value) {
    if (typeof value !== 'string' || !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)) return null;
    return Number(value.slice(0, 2)) * 60 + Number(value.slice(3));
}

function calculateShift(shift) {
    const start = clockMinute(shift.start);
    const end = clockMinute(shift.end);
    if (start === null || end === null) throw new Error('ডিউটি শুরুর ও শেষের সময় দিন।');
    if (start === end) throw new Error('শুরু ও শেষ একই সময়। শিফটের সময় দুটো দেখে নিন।');
    const overnight = end < start;
    const elapsed = end - start + (overnight ? 1440 : 0);
    if (!Number.isInteger(shift.breakMinutes) || shift.breakMinutes < 0 || shift.breakMinutes > elapsed) {
        throw new Error('বিরতি মিনিটে দিন। এটি শিফটের মোট সময়ের মধ্যে হতে হবে।');
    }
    if (!Number.isInteger(shift.regularMinutes) || shift.regularMinutes < 0 || shift.regularMinutes > 1440) {
        throw new Error('নিয়মিত কাজের সময় ০ থেকে ২৪ ঘণ্টার মধ্যে দিন। যেমন ৮ বা ৭:৩০।');
    }
    const worked = elapsed - shift.breakMinutes;
    return {overnight, elapsed, worked, otMinutes: Math.max(0, worked - shift.regularMinutes)};
}

function cleanShift(value) {
    if (!value || typeof value !== 'object') return null;
    const {start, end, breakMinutes, regularMinutes} = value;
    const shift = {start, end, breakMinutes, regularMinutes};
    try {
        calculateShift(shift);
        return shift;
    } catch {
        return null;
    }
}

function shiftRange(shift) {
    return toBnNum(shift.start) + ' – ' + toBnNum(shift.end)
        + (shift.end < shift.start ? ' (পরের দিন)' : '');
}

function shiftFromForm() {
    const breakText = asciiDigits(document.getElementById('shiftBreak').value);
    let regularMinutes;
    try {
        regularMinutes = readPaidMinutes(document.getElementById('shiftRegular').value);
    } catch {
        throw new Error('নিয়মিত কাজের সময় ঘণ্টায় লিখুন। যেমন ৮ বা ৭:৩০।');
    }
    return {
        start: document.getElementById('shiftStart').value,
        end: document.getElementById('shiftEnd').value,
        breakMinutes: /^\d{1,4}$/.test(breakText) ? Number(breakText) : null,
        regularMinutes
    };
}

function previewShift() {
    const result = document.getElementById('shiftResult');
    const detail = document.getElementById('shiftBreakdown');
    const save = document.getElementById('saveShift');
    try {
        const shift = shiftFromForm();
        const calc = calculateShift(shift);
        result.textContent = 'OT ' + hoursAndMinutes(calc.otMinutes);
        detail.textContent = (calc.overnight ? 'শেষ হবে পরের দিন। ' : '')
            + 'শিফট ' + hoursAndMinutes(calc.elapsed) + ' • বিরতি বাদে কাজ ' + hoursAndMinutes(calc.worked);
        save.disabled = false;
    } catch (error) {
        result.textContent = 'OT হিসাব';
        detail.textContent = error.message;
        save.disabled = true;
    }
}

function renderShiftForm(entry) {
    const form = document.getElementById('shiftForm');
    const shift = cleanShift(entry.shift);
    const identity = selectedDateStr + JSON.stringify(shift);
    if (form.dataset.saved !== identity) {
        document.getElementById('shiftStart').value = shift ? shift.start : '';
        document.getElementById('shiftEnd').value = shift ? shift.end : '';
        document.getElementById('shiftBreak').value = toBnNum(shift ? shift.breakMinutes : 0);
        const regular = shift ? shift.regularMinutes : 480;
        document.getElementById('shiftRegular').value = toBnNum(regular % 60 ? minuteClock(regular) : regular / 60);
        form.dataset.saved = identity;
    }
    const summary = document.getElementById('shiftSaved');
    let status = '';
    if (shift) {
        status = shiftRange(shift);
        if (otMinutesOf(entry) !== calculateShift(shift).otMinutes) {
            status += ' • হাতে ঠিক করা OT ' + hoursAndMinutes(otMinutesOf(entry));
        }
    }
    summary.textContent = status;
    const replaces = !!entry.absent || otMinutesOf(entry) > 0 || !!shift;
    document.getElementById('shiftSaveHint').textContent = entry.absent
        ? 'সেভ করলে এই দিনের অনুপস্থিতি সরিয়ে শিফট রাখা হবে।'
        : replaces ? 'সেভ করলে এই দিনের OT শিফটের হিসাব অনুযায়ী বদলাবে।' : '';
    document.getElementById('saveShift').textContent = shift ? 'শিফট আপডেট করুন' : 'শিফট সেভ করুন';
    previewShift();
}

function saveSelectedShift(event) {
    event.preventDefault();
    let shift, calc;
    try {
        shift = shiftFromForm();
        calc = calculateShift(shift);
    } catch {
        previewShift();
        return;
    }
    const current = getEntry(selectedDateStr);
    const tiffin = current.tiffin || (appSettings.autoTiffin && calc.otMinutes >= 120 &&
        (!current.shift || otMinutesOf(current) < 120));
    if (saveEntry(selectedDateStr, {...current, shift, otMinutes: calc.otMinutes, absent: false, tiffin})) {
        showToast('শিফট সেভ হয়েছে। OT ' + hoursAndMinutes(calc.otMinutes));
    }
}
