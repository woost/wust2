package wust.webUtil

import outwatch.dom.VDomModifier

case class ModalConfig(header: VDomModifier, description: VDomModifier, onClose: () => Boolean = () => true, actions: Option[VDomModifier] = None, modalModifier: VDomModifier = VDomModifier.empty, contentModifier: VDomModifier = VDomModifier.empty)


