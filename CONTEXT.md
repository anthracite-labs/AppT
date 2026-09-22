# AppT

AppT is a phone-native television remote. Its domain separates the physical television, this phone's local trust/personalization, and the customer's minimal licensing account so cloud identity never becomes the owner of TV data.

## Language

**Television**:
The physical television AppT may discover and control. It exists independently of any phone, pairing, account, or friendly name.
_Avoid_: Device, account TV, cloud TV

**Discovered Television**:
A television observation produced by the current phone's bounded local-network discovery before the phone necessarily trusts or remembers it.
_Avoid_: Saved TV, paired TV

**Remembered Television**:
A television this phone has retained local state for so it can be reopened or presented without treating it as a fresh discovery.
_Avoid_: Synced TV, account TV

**Local Pairing**:
The trust relationship established between one phone and one television, including any device-local credentials or security identity required to reconnect.
_Avoid_: Account pairing, shared pairing

**Television Personalization**:
Device-local user choices attached to a television, such as its friendly name, favourites, and secondary-control order.
_Avoid_: Profile, cloud personalization

**Interaction Preferences**:
Device-wide user choices for how AppT behaves on this phone, such as haptics, physical volume-button behavior, and preferred navigation mode.
_Avoid_: TV settings, synced preferences

**Active Remote**:
The currently active local-control experience for one television on this phone.
_Avoid_: Account session

**Customer Account**:
The minimal AppT identity used only for display username, trial state, and lifetime license entitlement. It does not own television, pairing, personalization, diagnostics, or behavioral data.
_Avoid_: User profile, TV account

**Username**:
An editable, non-unique display name attached to a Customer Account. It is not a login identifier, public handle, or social identity.
_Avoid_: Handle, account id, login name

**Trial**:
The seven-day period during which an eligible Customer Account may use AppT fully before a lifetime entitlement is required.
_Avoid_: Subscription, recurring plan

**Lifetime Entitlement**:
The durable customer right to continued AppT use after an authoritative one-time purchase has been validated.
_Avoid_: Subscription, premium tier

**Forget this TV**:
The consumer action that removes this phone's local relationship with one remembered television, including its pairing and television personalization.
_Avoid_: Remove from account, cloud delete
