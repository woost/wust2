package wust.webUtil

import outwatch.dom.VDomModifier

final case class ModalConfig(header: VDomModifier, description: VDomModifier, onHide: () => Boolean = () => true, actions: Option[VDomModifier] = None, modalModifier: VDomModifier = VDomModifier.empty, contentModifier: VDomModifier = VDomModifier.empty)


